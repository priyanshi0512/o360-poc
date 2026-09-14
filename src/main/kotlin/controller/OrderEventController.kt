package com.ikea.o360.controller

import com.ikea.o360.dto.OrderEventAcknowledgement
import com.ikea.o360.service.OrderEventService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

@RestController
@RequestMapping("/api/v1")
class OrderEventController(
    private val orderEventService: OrderEventService
) {

    @PostMapping("/order-events")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: JsonNode
    ): OrderEventAcknowledgement {
        return orderEventService.create(request)
    }
}