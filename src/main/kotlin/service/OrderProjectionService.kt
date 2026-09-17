package com.ikea.o360.service

import com.ikea.o360.domain.read.Order
import com.ikea.o360.domain.write.OrderEvent
import com.ikea.o360.domain.read.OrderItem
import com.ikea.o360.repository.read.OrderItemReadRepository
import com.ikea.o360.repository.read.OrderReadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.util.UUID

/**
 * Writes the read store (`orders` / `order_items`) from events.
 *
 * - [project]: incremental apply of a single (in-order) event.
 * - [replay]: rebuild one order from its full history (correctness path for out-of-order
 *   or multi-event reconstruction).
 * Both funnel into [applyProjection], which is where the read-store SAVE happens.
 */
@Service
class OrderProjectionService(
    private val orderReadRepository: OrderReadRepository,
    private val orderItemReadRepository: OrderItemReadRepository
) {

    private val json = JsonMapper.builder().build()

    /** Incremental: apply a single event to the read store. */
    @Transactional("readTransactionManager")
    fun project(event: OrderEvent) {
        applyProjection(event)
    }

    /** Rebuild one order deterministically: clear it, then re-apply its history in order. */
    @Transactional("readTransactionManager")
    fun replay(orderId: UUID, history: List<OrderEvent>) {
        orderItemReadRepository.deleteByOrder_OrderId(orderId)
        orderReadRepository.findById(orderId).ifPresent { orderReadRepository.delete(it) }
        orderReadRepository.flush()
        history.forEach { applyProjection(it) }
    }

    /** The single place where the read model is written. */
    private fun applyProjection(event: OrderEvent) {
        val payload = json.readTree(event.payload)

        // Validate order_lines up front (array of objects, each with a non-blank integer seq).
        val lines = validateOrderLines(payload)

        val order = orderReadRepository.findById(event.orderId).orElseGet {
            Order(
                orderId = event.orderId,
                userId = requiredUuid(payload, "user_id"),
                createdTimestamp = event.timestamp,
                lastUpdatedTimestamp = event.timestamp,
                status = "UNKNOWN", // placeholder; derived from line statuses below
                timeline = event.payload,
                items = mutableListOf()
            )
        }

        // Keep the earliest known timestamp as creation time.
        if (event.timestamp.isBefore(order.createdTimestamp)) {
            order.createdTimestamp = event.timestamp
        }

        // For each line -> saveItem (insert whole line JSON, or update status + deep-merge details).
        for (line in lines) {
            val seq = line.get("order_line_seq").asString().trim().toInt()
            saveItem(order, seq, line)
        }

        // Order status = majority (mode) of all line statuses.
        order.status = deriveOrderStatus(order)
        order.lastUpdatedTimestamp = event.timestamp
        order.timeline = event.payload

        // Read DB save (items cascade from the Order aggregate), then flush order_items.
        orderReadRepository.save(order)
        orderItemReadRepository.flush()
    }

    /**
     * Validate the payload's `order_lines`: it must be an array of JSON objects, each carrying a
     * non-blank, integer `order_line_seq`. Returns the lines (empty if the field is absent).
     */
    private fun validateOrderLines(payload: JsonNode): List<JsonNode> {
        val lines = payload.get("order_lines") ?: return emptyList()
        require(lines.isArray) { "'order_lines' must be an array" }
        return lines.map { line ->
            require(line.isObject) { "each 'order_lines' entry must be a JSON object" }
            val seq = line.get("order_line_seq")
            require(seq != null && !seq.isNull && seq.asString().isNotBlank()) {
                "each order line requires a non-blank 'order_line_seq'"
            }
            require(seq.asString().trim().toIntOrNull() != null) {
                "'order_line_seq' must be an integer, got '${seq.asString()}'"
            }
            line
        }
    }

    /**
     * Upsert a single order line into the read model, keyed by `order_line_seq`:
     * - not present  -> INSERT storing the whole line JSON,
     * - already there -> UPDATE its status and deep-merge the new details into the stored JSON
     *   (so unknown/future fields from earlier events are preserved).
     */
    private fun saveItem(order: Order, seq: Int, line: JsonNode) {
        val status = line.get("status")?.takeIf { !it.isNull }?.asString() ?: "UNKNOWN"
        val existing = order.items.firstOrNull { it.orderItemSequence == seq }
        if (existing == null) {
            order.items.add(
                OrderItem(
                    id = deterministicItemId(order.orderId, seq),
                    order = order,
                    orderItemSequence = seq,
                    status = status,
                    orderDetails = line.toString()
                )
            )
        } else {
            existing.status = status
            existing.orderDetails = deepMerge(json.readTree(existing.orderDetails), line).toString()
        }
    }

    /**
     * Derive the order status as the majority (mode) of its line statuses. Ties are broken
     * alphabetically, but the previous status wins if it is still among the modes.
     */
    private fun deriveOrderStatus(order: Order): String {
        val statuses = order.items.map { it.status.uppercase() }
        if (statuses.isEmpty()) return order.status

        val counts = statuses.groupingBy { it }.eachCount()
        val max = counts.values.max()
        val modes = counts.filterValues { it == max }.keys
        return if (order.status.uppercase() in modes) order.status else modes.min()
    }

    /** Recursively merge [overlay] onto [base]; overlapping objects merge, other values overwrite. */
    private fun deepMerge(base: JsonNode, overlay: JsonNode): JsonNode {
        if (base !is ObjectNode || overlay !is ObjectNode) return overlay
        val merged: ObjectNode = base.deepCopy()
        for (entry in overlay.properties()) {
            val existing = merged.get(entry.key)
            if (existing is ObjectNode && entry.value is ObjectNode) {
                merged.set(entry.key, deepMerge(existing, entry.value))
            } else {
                merged.set(entry.key, entry.value)
            }
        }
        return merged
    }

    /** Deterministic item id so re-projecting the same line upserts the same row. */
    private fun deterministicItemId(orderId: UUID, seq: Int): UUID =
        UUID.nameUUIDFromBytes("$orderId:$seq".toByteArray())


    private fun requiredUuid(payload: JsonNode, field: String): UUID {
        val value = payload.get(field)
        require(value != null && !value.isNull) { "Missing required field: '$field'" }
        return UUID.fromString(value.asString())
    }
}

