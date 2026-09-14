package com.ikea.o360.domain.read

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "order_items")
class OrderItem(

    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order,

    @Column(name = "order_item_sequence", nullable = false)
    var orderItemSequence: Int,

    @Column(name = "status", nullable = false)
    var status: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "order_details", nullable = false, columnDefinition = "jsonb")
    var orderDetails: String
)
