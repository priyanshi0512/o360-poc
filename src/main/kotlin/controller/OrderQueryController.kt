package com.ikea.o360.controller

import com.ikea.o360.dto.OrderItemResponse
import com.ikea.o360.dto.OrderResponse
import com.ikea.o360.dto.OrderSummaryResponse
import com.ikea.o360.service.OrderQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Query side (CQRS read) endpoints, served from the projected read store.
 */
@RestController
@RequestMapping("/api/v1")
class OrderQueryController(
    private val orderQueryService: OrderQueryService
) {

    /** List of orders. */
    @GetMapping("/orders")
    fun listOrders(): List<OrderSummaryResponse> = orderQueryService.listOrders()

    /** Order by id. */
    @GetMapping("/orders/{orderId}")
    fun getOrder(@PathVariable orderId: UUID): OrderResponse = orderQueryService.getOrder(orderId)

    /** Items under an order. */
    @GetMapping("/orders/{orderId}/items")
    fun getItems(@PathVariable orderId: UUID): List<OrderItemResponse> = orderQueryService.getItems(orderId)
}

