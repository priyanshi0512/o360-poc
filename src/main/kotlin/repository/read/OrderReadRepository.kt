package com.ikea.o360.repository.read

import com.ikea.o360.domain.read.Order
import com.ikea.o360.domain.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Read store for orders. The projection writes into it and the query side reads from it.
 */
interface OrderReadRepository : JpaRepository<Order, UUID> {

    fun findByUserId(userId: UUID): List<Order>

    fun findByStatus(status: OrderStatus): List<Order>
}
