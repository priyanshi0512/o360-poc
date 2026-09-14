package com.ikea.o360.repository.read

import com.ikea.o360.domain.read.OrderItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Read store for order items. Used by the query side (items under an order) and by
 * the projection's replay path (clear an order's items before re-applying history).
 */
interface OrderItemReadRepository : JpaRepository<OrderItem, UUID> {

    /** Items under an order, ordered by their sequence. */
    fun findByOrder_OrderIdOrderByOrderItemSequenceAsc(orderId: UUID): List<OrderItem>

    /** Remove all items for an order (used before a replay). */
    fun deleteByOrder_OrderId(orderId: UUID)
}

