package com.ikea.o360

import com.ikea.o360.service.OrderEventService
import com.ikea.o360.service.OrderQueryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/**
 * End-to-end CQRS flow across the two physically-separated stores:
 *
 *   write-db (Testcontainers) --[OrderEventService.create]--> order_events
 *        --[projection coordinator]--> read-db (Testcontainers): orders / order_items
 *        --[OrderQueryService]--> response DTOs
 *
 * Each store gets its own Postgres container; Flyway migrates each on startup.
 */
@SpringBootTest
@Testcontainers
class OrderFlowIntegrationTest {

    @Autowired
    lateinit var orderEventService: OrderEventService

    @Autowired
    lateinit var orderQueryService: OrderQueryService

    @Autowired
    lateinit var orderEventRepository: com.ikea.o360.repository.write.OrderEventRepository

    private val json = JsonMapper.builder().build()

    @Test
    fun `event is written to the write store then projected into the read store`() {
        val orderId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val request = json.readTree(
            """
            {
              "order_id": "$orderId",
              "event_type": "ORDER_CREATED",
              "timestamp": "2026-09-02T00:34:56Z",
              "created_by": "test-user",
              "user_id": "$userId",
              "status": "CREATED",
              "future_field": "preserved unchanged",
              "order_lines": [
                { "order_line_seq": "1", "status": "CREATED", "sku": "SKU-001", "quantity": 2 }
              ]
            }
            """.trimIndent()
        )

        // --- Write path ---
        val ack = orderEventService.create(request)
        assertNotNull(ack.eventId)
        assertEquals("ACCEPTED", ack.status)

        // --- Read path (projected into the read-db) ---
        val order = orderQueryService.getOrder(orderId)
        assertEquals(orderId, order.orderId)
        assertEquals(userId, order.userId)
        assertEquals("CREATED", order.status)
        // Raw payload is preserved in the timeline (including unknown fields).
        assertEquals("preserved unchanged", order.timeline.get("future_field").asString())

        val items = orderQueryService.getItems(orderId)
        assertEquals(1, items.size)
        assertEquals(1, items[0].orderItemSequence)
        assertEquals("CREATED", items[0].status)
        assertEquals("SKU-001", items[0].orderDetails.get("sku").asString())
    }

    @Test
    fun `a newer event advances the projected order status`() {
        val orderId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        orderEventService.create(
            json.readTree(
                """
                { "order_id": "$orderId", "event_type": "ORDER_CREATED",
                  "timestamp": "2026-09-02T00:00:00Z", "user_id": "$userId",
                  "order_lines": [ { "order_line_seq": "1", "status": "CREATED" } ] }
                """.trimIndent()
            )
        )
        orderEventService.create(
            json.readTree(
                """
                { "order_id": "$orderId", "event_type": "ORDER_SHIPPED",
                  "timestamp": "2026-09-03T00:00:00Z", "user_id": "$userId",
                  "order_lines": [ { "order_line_seq": "1", "status": "SHIPPED" } ] }
                """.trimIndent()
            )
        )

        val order = orderQueryService.getOrder(orderId)
        assertEquals("SHIPPED", order.status)
    }

    @Test
    fun `rejects an event missing the required event_type`() {
        val request = json.readTree("""{ "order_id": "${UUID.randomUUID()}" }""")
        val ex = assertThrows(IllegalArgumentException::class.java) {
            orderEventService.create(request)
        }
        assertTrue(ex.message!!.contains("event_type"), "message was: ${ex.message}")
    }

    @Test
    fun `rejects an event with a malformed order_id`() {
        val request = json.readTree(
            """{ "order_id": "not-a-uuid", "event_type": "ORDER_CREATED" }"""
        )
        val ex = assertThrows(IllegalArgumentException::class.java) {
            orderEventService.create(request)
        }
        assertTrue(ex.message!!.contains("valid UUID"), "message was: ${ex.message}")
    }

    @Test
    fun `duplicate event_id is idempotent - stored once, same acknowledgement`() {
        val orderId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val body = """
            { "event_id": "$eventId", "order_id": "$orderId", "event_type": "ORDER_CREATED",
              "timestamp": "2026-09-02T00:00:00Z", "user_id": "${UUID.randomUUID()}",
              "order_lines": [ { "order_line_seq": "1", "status": "CREATED" } ] }
        """.trimIndent()

        val first = orderEventService.create(json.readTree(body))
        val second = orderEventService.create(json.readTree(body))

        // Same acknowledgement returned both times (no new event created).
        assertEquals(first.eventId, second.eventId)
        assertEquals(first.receivedTimestamp, second.receivedTimestamp)

        // Only one event actually persisted for this order.
        assertEquals(1, orderEventRepository.findByOrderIdOrderByTimestampAscRandomIdAsc(orderId).size)
    }

    companion object {
        @Container
        @JvmStatic
        val writeDb: PostgreSQLContainer<Nothing> =
            PostgreSQLContainer<Nothing>("postgres:17-alpine").apply {
                withDatabaseName("o360_write")
                withUsername("postgres")
                withPassword("postgres")
            }

        @Container
        @JvmStatic
        val readDb: PostgreSQLContainer<Nothing> =
            PostgreSQLContainer<Nothing>("postgres:17-alpine").apply {
                withDatabaseName("o360_read")
                withUsername("postgres")
                withPassword("postgres")
            }

        @DynamicPropertySource
        @JvmStatic
        fun datasourceProps(registry: DynamicPropertyRegistry) {
            registry.add("write.datasource.url", writeDb::getJdbcUrl)
            registry.add("write.datasource.username", writeDb::getUsername)
            registry.add("write.datasource.password", writeDb::getPassword)
            registry.add("read.datasource.url", readDb::getJdbcUrl)
            registry.add("read.datasource.username", readDb::getUsername)
            registry.add("read.datasource.password", readDb::getPassword)
        }
    }
}

