package com.ikea.o360.service

import com.ikea.o360.domain.write.OrderEvent
import com.ikea.o360.dto.OrderEventAcknowledgement
import com.ikea.o360.repository.write.OrderEventRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Handles incoming raw order events:
 *   JSON request -> persist to order_events (write store, committed on its own)
 *   -> hand off the event id to the projection coordinator -> return an acknowledgement.
 */
@Service
class OrderEventService(
    private val orderEventRepository: OrderEventRepository,
    private val projectionCoordinator: OrderEventProjectionCoordinator
) {

    fun create(request: JsonNode): OrderEventAcknowledgement {
        // Validate & extract the required fields from the incoming JSON payload.
        val eventType = request.requiredText("event_type")
        val orderId = request.requiredUuid("order_id")
        // Optional fields: validate their format only if present (keep raw-event tolerance).
        request.optionalUuid("user_id")
        val createdBy = request.optionalText("created_by") ?: "system"
        // Optional idempotency key: if we've already stored this event, return the original ack.
        val eventId = request.optionalUuid("event_id")
        if (eventId != null) {
            orderEventRepository.findByEventId(eventId)?.let { return it.toAcknowledgement() }
        }

        val now = Instant.now()
        // Business event time comes from the payload; fall back to ingestion time.
        val eventTime = request.optionalInstant("timestamp") ?: now

        // Build the entity, keeping the full raw payload for auditing/replay.
        val orderEvent = OrderEvent(
            randomId = UUID.randomUUID(),
            orderId = orderId,
            eventType = eventType,
            timestamp = eventTime,
            payload = request.toString(),
            createdBy = createdBy,
            createdAt = now,
            eventId = eventId
        )

        // Persist to the order_events table (write store) — committed here.
        val saved = try {
            orderEventRepository.save(orderEvent)
        } catch (ex: DataIntegrityViolationException) {
            // Lost a race: another request stored the same idempotency key concurrently.
            eventId?.let { orderEventRepository.findByEventId(it) }?.let { return it.toAcknowledgement() }
            throw ex
        }

        // Hand off only the event id; the coordinator loads it and updates the read store.
        projectionCoordinator.process(saved.randomId)

        return saved.toAcknowledgement()
    }

    private fun OrderEvent.toAcknowledgement() =
        OrderEventAcknowledgement(eventId = randomId, receivedTimestamp = createdAt)

    private fun JsonNode.requiredText(field: String): String {
        val value = this.get(field)
        require(value != null && !value.isNull && value.asString().isNotBlank()) {
            "Missing or blank required field: '$field'"
        }
        return value.asString()
    }

    private fun JsonNode.optionalText(field: String): String? {
        val value = this.get(field)
        return if (value == null || value.isNull) null else value.asString()
    }

    private fun JsonNode.requiredUuid(field: String): UUID = parseUuid(field, requiredText(field))

    private fun JsonNode.optionalUuid(field: String): UUID? {
        val text = optionalText(field) ?: return null
        return parseUuid(field, text)
    }

    private fun parseUuid(field: String, text: String): UUID =
        try {
            UUID.fromString(text)
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("Field '$field' must be a valid UUID, got '$text'")
        }

    private fun JsonNode.optionalInstant(field: String): Instant? {
        val text = optionalText(field) ?: return null
        return try {
            Instant.parse(text)
        } catch (ex: DateTimeParseException) {
            throw IllegalArgumentException("Field '$field' must be an ISO-8601 instant, got '$text'")
        }
    }
}