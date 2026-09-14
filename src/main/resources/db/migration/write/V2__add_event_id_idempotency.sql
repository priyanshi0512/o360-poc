-- Idempotency key: an optional client-supplied event id. When provided, it must be
-- unique so retries/duplicates collapse to a single event. NULLs are allowed and are
-- treated as distinct by Postgres, so events without a key are simply not deduplicated.
ALTER TABLE order_events ADD COLUMN event_id UUID;

CREATE UNIQUE INDEX uq_order_events_event_id ON order_events (event_id);

