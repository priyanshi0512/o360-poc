package com.ikea.o360.service

import com.ikea.o360.domain.read.Order
import com.ikea.o360.domain.write.OrderEvent
import com.ikea.o360.domain.read.OrderItem
import com.ikea.o360.domain.OrderStatus
import com.ikea.o360.repository.read.OrderItemReadRepository
import com.ikea.o360.repository.read.OrderReadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
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

        val order = orderReadRepository.findById(event.orderId).orElseGet {
            Order(
                orderId = event.orderId,
                userId = requiredUuid(payload, "user_id"),
                createdTimestamp = event.timestamp,
                lastUpdatedTimestamp = event.timestamp,
                status = mapStatus(event, payload),
                timeline = event.payload,
                items = mutableListOf()
            )
        }

        // Keep the earliest known timestamp as creation time.
        if (event.timestamp.isBefore(order.createdTimestamp)) {
            order.createdTimestamp = event.timestamp
        }

        // Update mutable read-model state from this event.
        order.status = mapStatus(event, payload)
        order.lastUpdatedTimestamp = event.timestamp
        order.timeline = event.payload

        applyOrderLines(order, payload)

        // Read DB save (items cascade from the Order aggregate).
        orderReadRepository.save(order)
    }

    /** Upsert order items from the payload's `order_lines`, keyed by `order_line_seq`. */
    private fun applyOrderLines(order: Order, payload: JsonNode) {
        val lines = payload.get("order_lines")
        if (lines == null || !lines.isArray) return

        val bySeq = order.items.associateBy { it.orderItemSequence }
        val seenSeqs = mutableSetOf<Int>()

        for (line in lines) {
            val seq = line.get("order_line_seq")?.takeIf { !it.isNull }?.asString()?.toInt() ?: continue
            seenSeqs += seq
            val status = line.get("status")?.takeIf { !it.isNull }?.asString() ?: "UNKNOWN"

            val existing = bySeq[seq]
            if (existing != null) {
                existing.status = status
                existing.orderDetails = line.toString()
            } else {
                order.items.add(
                    OrderItem(
                        id = deterministicItemId(order.orderId, seq),
                        order = order,
                        orderItemSequence = seq,
                        status = status,
                        orderDetails = line.toString()
                    )
                )
            }
        }

        // Drop lines that are no longer present (orphanRemoval deletes them).
        order.items.removeIf { it.orderItemSequence !in seenSeqs }
    }

    /** Deterministic item id so re-projecting the same line upserts the same row. */
    private fun deterministicItemId(orderId: UUID, seq: Int): UUID =
        UUID.nameUUIDFromBytes("$orderId:$seq".toByteArray())

    private fun mapStatus(event: OrderEvent, payload: JsonNode): OrderStatus {
        when (event.eventType.uppercase()) {
            "ORDER_CREATED" -> return OrderStatus.CREATED
            "ORDER_PROCESSING", "ORDER_PROCESSED" -> return OrderStatus.PROCESSING
            "ORDER_SHIPPED" -> return OrderStatus.SHIPPED
            "ORDER_DELIVERED" -> return OrderStatus.DELIVERED
            "ORDER_CANCELLED", "ORDER_CANCELED" -> return OrderStatus.CANCELLED
        }
        // Fallback: a "status" field in the payload, else default to CREATED.
        val statusText = payload.get("status")?.takeIf { !it.isNull }?.asString()
        return statusText
            ?.let { runCatching { OrderStatus.valueOf(it.uppercase()) }.getOrNull() }
            ?: OrderStatus.CREATED
    }

    private fun requiredUuid(payload: JsonNode, field: String): UUID {
        val value = payload.get(field)
        require(value != null && !value.isNull) { "Missing required field: '$field'" }
        return UUID.fromString(value.asString())
    }
}

