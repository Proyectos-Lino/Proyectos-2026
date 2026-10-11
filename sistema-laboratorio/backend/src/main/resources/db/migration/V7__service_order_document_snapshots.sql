CREATE TABLE service_order_document (
    id UUID PRIMARY KEY,
    service_order_id UUID NOT NULL UNIQUE REFERENCES service_order(id),
    version INTEGER NOT NULL,
    snapshot_content TEXT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    generated_by VARCHAR(180) NOT NULL
);

CREATE INDEX idx_service_order_document_generated
    ON service_order_document(generated_at DESC);
