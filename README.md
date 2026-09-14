# o360-poc

A proof-of-concept **CQRS + event-sourcing-lite** order service built with **Kotlin**, **Spring Boot 4**, and **PostgreSQL**. Order events are the source of truth; a projection derives a queryable read model.

## Architecture

Two **physically separated** Postgres databases:

| Store | Database | Tables | Role |
|-------|----------|--------|------|
| **Write** | `o360_write` (port 5433) | `order_events` | Append-only source of truth |
| **Read**  | `o360_read` (port 5434) | `orders`, `order_items` | Projected, queryable read model |

```
POST /api/v1/order-events
      │  (write store — committed)
      ▼
OrderEventService ──► order_events (write-db)
      │  hands off eventId
      ▼
OrderEventProjectionCoordinator ──► project(event)  OR  replay(orderId, history)
      │
      ▼
OrderProjectionService ──► orders / order_items (read-db)

GET /api/v1/orders            ─┐
GET /api/v1/orders/{id}        ├─► OrderQueryService ──► read-db
GET /api/v1/orders/{id}/items ─┘
```

- **project** = fast path for in-order events.
- **replay** = correctness fallback that rebuilds one order from its full event history (handles out-of-order / multi-event reconstruction).
- The read store is a **disposable cache** — it can always be rebuilt by replaying events.

## Tech

- Kotlin 2.2 / JVM 17
- Spring Boot 4 (web, data-jpa) with **two datasources** (`writeTransactionManager` / `readTransactionManager`)
- Jackson 3 (`tools.jackson`) — JSON payloads stored as `jsonb`
- Flyway migrations per datasource (`db/migration/write`, `db/migration/read`)
- Testcontainers integration test spanning both databases

## API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/order-events` | Ingest a raw order event (JSON) |
| `GET`  | `/api/v1/orders` | List orders |
| `GET`  | `/api/v1/orders/{orderId}` | Get an order (with timeline + items) |
| `GET`  | `/api/v1/orders/{orderId}/items` | List items under an order |

Required event fields: `order_id`, `event_type`.

## Run

### With Docker (both databases + app)
```bash
docker compose up -d --build
```

### Locally (needs the two databases running)
```bash
# start just the databases
docker compose up -d write-db read-db
# run the app
mvn spring-boot:run
```

Datasource connection is configurable via env vars:
`WRITE_DB_URL`, `WRITE_DB_USERNAME`, `WRITE_DB_PASSWORD`, `READ_DB_URL`, `READ_DB_USERNAME`, `READ_DB_PASSWORD`.

### Example

**1. Ingest an order event** (`POST /api/v1/order-events`)
```bash
curl -i -X POST http://localhost:8080/api/v1/order-events \
  -H 'Content-Type: application/json' \
  -d '{
    "order_id": "b6ac61c0-5c7d-45a3-8b89-6b8020f8b937",
    "event_type": "ORDER_CREATED",
    "timestamp": "2026-09-02T00:34:56Z",
    "created_by": "local-user",
    "user_id": "0e7fc15e-04c0-4e72-960e-7706f580e172",
    "status": "CREATED",
    "future_field": "preserved unchanged",
    "order_lines": [
      {
        "order_line_seq": "1",
        "status": "CREATED",
        "sku": "SKU-001",
        "productName": "Example item",
        "quantity": 2,
        "price": 49.99
      }
    ]
  }'
```
Returns `201 Created` with an acknowledgement:
```json
{ "eventId": "…", "receivedTimestamp": "…", "status": "ACCEPTED" }
```
Required fields are `order_id` and `event_type` (omitting either returns `400`).

**2. Advance the order** — post a later event for the same `order_id`:
```bash
curl -i -X POST http://localhost:8080/api/v1/order-events \
  -H 'Content-Type: application/json' \
  -d '{
    "order_id": "b6ac61c0-5c7d-45a3-8b89-6b8020f8b937",
    "event_type": "ORDER_SHIPPED",
    "timestamp": "2026-09-03T10:00:00Z",
    "user_id": "0e7fc15e-04c0-4e72-960e-7706f580e172",
    "order_lines": [ { "order_line_seq": "1", "status": "SHIPPED" } ]
  }'
```

**3. Query the read model**
```bash
# list all orders
curl http://localhost:8080/api/v1/orders

# get one order (with timeline + items)
curl http://localhost:8080/api/v1/orders/b6ac61c0-5c7d-45a3-8b89-6b8020f8b937

# items under an order
curl http://localhost:8080/api/v1/orders/b6ac61c0-5c7d-45a3-8b89-6b8020f8b937/items
```

## Test

```bash
mvn test
```

Runs an end-to-end integration test that starts two Postgres containers (write + read) via Testcontainers and verifies the full write → projection → read flow.

## Project layout

```
src/main/kotlin/com/ikea/o360/
  config/       # two datasource configs (write/read)
  controller/   # REST endpoints + global exception handling
  domain/
    write/      # OrderEvent entity
    read/       # Order, OrderItem entities
  dto/          # request/response models
  repository/
    write/      # OrderEventRepository
    read/       # OrderReadRepository, OrderItemReadRepository
  service/      # event ingestion, projection coordinator, projection, query
src/main/resources/
  db/migration/write|read/   # Flyway migrations per store
```

