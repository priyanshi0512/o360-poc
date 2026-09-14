package com.ikea.o360.repository.write

import com.ikea.o360.domain.write.OrderEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderEventRepository : JpaRepository<OrderEvent, UUID> {

    /** Full event history for an order, chronologically (ties broken by id) — used by replay. */
    fun findByOrderIdOrderByTimestampAscRandomIdAsc(orderId: UUID): List<OrderEvent>
}

