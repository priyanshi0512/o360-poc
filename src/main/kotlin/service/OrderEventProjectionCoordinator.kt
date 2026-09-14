package com.ikea.o360.service

import com.ikea.o360.domain.write.OrderEvent
import com.ikea.o360.repository.read.OrderReadRepository
import com.ikea.o360.repository.write.OrderEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Decides how a committed event is written into the read store.
 *
 * Flow:
 *   eventId -> load event from Write DB -> look at read-side state + full history
 *           -> project(event)  (fast, in-order)  OR  replay(orderId, history)  (rebuild)
 *
 * Projection runs in its own transaction; if it fails we only log — the committed
 * write event is left intact and can be reprojected later.
 */
@Service
class OrderEventProjectionCoordinator(
    private val orderEventRepository: OrderEventRepository,
    private val orderReadRepository: OrderReadRepository,
    private val orderProjectionService: OrderProjectionService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun process(eventId: UUID) {
        val event = orderEventRepository.findById(eventId).orElse(null)
        if (event == null) {
            log.error("Event {} not found in write store; skipping projection", eventId)
            return
        }

        val orderId = event.orderId
        val previousTimestamp: Instant? =
            orderReadRepository.findById(orderId).map { it.lastUpdatedTimestamp }.orElse(null)
        val history = orderEventRepository.findByOrderIdOrderByTimestampAscRandomIdAsc(orderId)

        try {
            if (requiresReplay(previousTimestamp, event.timestamp, history)) {
                orderProjectionService.replay(orderId, history)
            } else {
                orderProjectionService.project(event)
            }
        } catch (ex: Exception) {
            log.error(
                "Projection failed for event {} (order {}); write event left intact for later rebuild",
                eventId, orderId, ex
            )
        }
    }

    /**
     * Replay when the event is out-of-order (its time is not after what we've already
     * applied), or when there's no read-side order yet but multiple events exist.
     */
    private fun requiresReplay(
        previousTimestamp: Instant?,
        eventTimestamp: Instant,
        history: List<OrderEvent>
    ): Boolean {
        if (previousTimestamp != null && !eventTimestamp.isAfter(previousTimestamp)) return true
        if (previousTimestamp == null && history.size > 1) return true
        return false
    }
}

