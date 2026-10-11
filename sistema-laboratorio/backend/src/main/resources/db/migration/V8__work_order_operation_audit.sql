ALTER TABLE work_order
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancellation_reason VARCHAR(1000),
    ADD COLUMN closed_at TIMESTAMPTZ;

CREATE TABLE work_order_history (
    id UUID PRIMARY KEY,
    work_order_id UUID NOT NULL REFERENCES work_order(id),
    action VARCHAR(50) NOT NULL,
    previous_status VARCHAR(30),
    new_status VARCHAR(30),
    change_note VARCHAR(1000),
    performed_by VARCHAR(180) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE work_order_document (
    id UUID PRIMARY KEY,
    work_order_id UUID NOT NULL UNIQUE REFERENCES work_order(id),
    version INTEGER NOT NULL,
    snapshot_content TEXT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    generated_by VARCHAR(180) NOT NULL
);

CREATE INDEX idx_work_order_history_order
    ON work_order_history(work_order_id, occurred_at DESC);

CREATE INDEX idx_work_order_document_generated
    ON work_order_document(generated_at DESC);
