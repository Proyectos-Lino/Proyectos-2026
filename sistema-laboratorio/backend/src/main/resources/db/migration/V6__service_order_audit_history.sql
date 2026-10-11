ALTER TABLE service_order
    ADD COLUMN cancellation_reason VARCHAR(1000);

CREATE TABLE service_order_history (
    id UUID PRIMARY KEY,
    service_order_id UUID NOT NULL REFERENCES service_order(id),
    action VARCHAR(50) NOT NULL,
    previous_status VARCHAR(30),
    new_status VARCHAR(30),
    change_note VARCHAR(1000),
    performed_by VARCHAR(180) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_service_order_area_created
    ON service_order(laboratory_area_id, created_at DESC);

CREATE INDEX idx_service_order_status_created
    ON service_order(status, created_at DESC);

CREATE INDEX idx_service_order_planned_delivery
    ON service_order(planned_delivery_at);

CREATE INDEX idx_service_order_history_order_occurred
    ON service_order_history(service_order_id, occurred_at DESC);
