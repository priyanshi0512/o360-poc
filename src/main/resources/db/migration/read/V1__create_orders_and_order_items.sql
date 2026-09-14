-- Core order aggregate.
CREATE TABLE orders (
    order_id               UUID         NOT NULL,
    user_id                UUID         NOT NULL,
    created_timestamp      TIMESTAMPTZ  NOT NULL,
    last_updated_timestamp TIMESTAMPTZ  NOT NULL,
    status                 VARCHAR(50)  NOT NULL,
    timeline               JSONB        NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (order_id)
);

-- Line items belonging to an order.
CREATE TABLE order_items (
    id                  UUID         NOT NULL,
    order_id            UUID         NOT NULL,
    order_item_sequence INTEGER      NOT NULL,
    status              VARCHAR(255) NOT NULL,
    order_details       JSONB        NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);

