CREATE TABLE event_outbox (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_key varchar(250) NOT NULL UNIQUE,
    event_type varchar(100) NOT NULL,
    aggregate_id uuid NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at timestamptz
);
CREATE INDEX ix_event_outbox_pending ON event_outbox(created_at, id) WHERE published_at IS NULL;
