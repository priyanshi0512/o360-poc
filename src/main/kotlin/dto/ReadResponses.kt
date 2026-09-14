package com.ikea.o360.dto

import com.ikea.o360.domain.OrderStatus
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

/**
 * Read-model responses for the query side. Kept separate from JPA entities so we
 * don't leak persistence concerns (lazy associations) and can shape JSON cleanly.
 */
data class OrderSummaryResponse(
    val orderId: UUID,
    val userId: UUID,
    val status: OrderStatus,
    val createdTimestamp: Instant,
    val lastUpdatedTimestamp: Instant
)

data class OrderItemResponse(
    val id: UUID,
    val orderItemSequence: Int,
    val status: String,
    val orderDetails: JsonNode
)

data class OrderResponse(
    val orderId: UUID,
    val userId: UUID,
    val status: OrderStatus,
    val createdTimestamp: Instant,
    val lastUpdatedTimestamp: Instant,
    val timeline: JsonNode,
    val items: List<OrderItemResponse>
)

