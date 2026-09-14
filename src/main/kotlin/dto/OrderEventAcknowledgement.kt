package com.ikea.o360.dto

import java.time.Instant
import java.util.UUID

/**
 * Acknowledgement returned to the caller after an order event has been accepted.
 */
data class OrderEventAcknowledgement(
    val eventId: UUID,
    val receivedTimestamp: Instant,
    val status: String = "ACCEPTED"
)

