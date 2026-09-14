package com.ikea.o360.service

import com.ikea.o360.domain.read.Order
import com.ikea.o360.domain.read.OrderItem
import com.ikea.o360.dto.OrderItemResponse
import com.ikea.o360.dto.OrderResponse
import com.ikea.o360.dto.OrderSummaryResponse
import com.ikea.o360.repository.read.OrderItemReadRepository
import com.ikea.o360.repository.read.OrderReadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/**
 * Query side (CQRS read). Reads projected orders from the read store and maps
 * them to response DTOs. The stored JSON strings are parsed back into JSON nodes
 * so they surface as real JSON in the API response.
 */
@Service
class OrderQueryService(
    private val orderReadRepository: OrderReadRepository,
    private val orderItemReadRepository: OrderItemReadRepository
) {

    private val json = JsonMapper.builder().build()

    @Transactional("readTransactionManager", readOnly = true)
    fun listOrders(): List<OrderSummaryResponse> =
        orderReadRepository.findAll().map { it.toSummary() }

    @Transactional("readTransactionManager", readOnly = true)
    fun getOrder(orderId: UUID): OrderResponse =
        orderReadRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order not found: $orderId") }
            .toResponse()

    @Transactional("readTransactionManager", readOnly = true)
    fun getItems(orderId: UUID): List<OrderItemResponse> {
        if (!orderReadRepository.existsById(orderId)) {
            throw NoSuchElementException("Order not found: $orderId")
        }
        return orderItemReadRepository
            .findByOrder_OrderIdOrderByOrderItemSequenceAsc(orderId)
            .map { it.toItemResponse() }
    }

    private fun Order.toSummary() = OrderSummaryResponse(
        orderId = orderId,
        userId = userId,
        status = status,
        createdTimestamp = createdTimestamp,
        lastUpdatedTimestamp = lastUpdatedTimestamp
    )

    private fun Order.toResponse() = OrderResponse(
        orderId = orderId,
        userId = userId,
        status = status,
        createdTimestamp = createdTimestamp,
        lastUpdatedTimestamp = lastUpdatedTimestamp,
        timeline = json.readTree(timeline),
        items = items.map { it.toItemResponse() }
    )

    private fun OrderItem.toItemResponse() = OrderItemResponse(
        id = id,
        orderItemSequence = orderItemSequence,
        status = status,
        orderDetails = json.readTree(orderDetails)
    )
}