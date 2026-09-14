package com.ikea.o360.dto

import java.time.Instant

/**
 * Standard error body returned for failed requests.
 */
data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val timestamp: Instant = Instant.now()
)

