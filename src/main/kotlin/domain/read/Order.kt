package com.ikea.o360.domain.read
import com.ikea.o360.domain.OrderStatus

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "orders")
class Order(

    @Id
    @Column(name = "order_id", nullable = false)
    var orderId: UUID,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "created_timestamp", nullable = false)
    var createdTimestamp: Instant,

    @Column(name = "last_updated_timestamp", nullable = false)
    var lastUpdatedTimestamp: Instant,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OrderStatus,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "timeline", nullable = false, columnDefinition = "jsonb")
    var timeline: String,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableList<OrderItem> = mutableListOf()
)

