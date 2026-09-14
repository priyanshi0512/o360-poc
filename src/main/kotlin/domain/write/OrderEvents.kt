package com.ikea.o360.domain.write

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Append-only order event, persisted to the `order_events` table.
 * Modeled directly as a JPA entity (no separate persistence class / mapper).
 */
@Entity
@Table(name = "order_events")
class OrderEvent(

    @Id
    @Column(name = "random_id", nullable = false)
    var randomId: UUID,

    @Column(name = "order_id", nullable = false)
    var orderId: UUID,

    @Column(name = "event_type", nullable = false)
    var eventType: String,

    @Column(name = "timestamp", nullable = false)
    var timestamp: Instant,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    var payload: String,

    @Column(name = "created_by", nullable = false)
    var createdBy: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant
)
