-- Write store: append-only order events.
CREATE TABLE order_events (
    random_id   UUID         NOT NULL,
    order_id    UUID         NOT NULL,
    event_type  VARCHAR(255) NOT NULL,
    timestamp   TIMESTAMPTZ  NOT NULL,
    payload     JSONB        NOT NULL,
    created_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_order_events PRIMARY KEY (random_id)
);

-- Common lookup: all events for a given order.
CREATE INDEX idx_order_events_order_id ON order_events (order_id);

-- Optional filter by event type.
CREATE INDEX idx_order_events_event_type ON order_events (event_type);

