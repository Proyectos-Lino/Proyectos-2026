CREATE TABLE laboratory_report (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    service_order_id UUID NOT NULL REFERENCES service_order(id),
    report_version INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    snapshot_json TEXT NOT NULL,
    pdf_content BYTEA NOT NULL,
    content_checksum VARCHAR(64) NOT NULL,
    created_by VARCHAR(180) NOT NULL,
    issued_by VARCHAR(180),
    issued_at TIMESTAMPTZ,
    cancelled_by VARCHAR(180),
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_laboratory_report_code UNIQUE (code),
    CONSTRAINT uq_laboratory_report_version UNIQUE (service_order_id, report_version),
    CONSTRAINT chk_laboratory_report_version CHECK (report_version > 0)
);

CREATE INDEX idx_laboratory_report_order ON laboratory_report(service_order_id, report_version DESC);
CREATE INDEX idx_laboratory_report_status ON laboratory_report(status, created_at DESC);

CREATE TABLE laboratory_report_history (
    id UUID PRIMARY KEY,
    laboratory_report_id UUID NOT NULL REFERENCES laboratory_report(id),
    action VARCHAR(30) NOT NULL,
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    change_note VARCHAR(1000),
    performed_by VARCHAR(180) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_laboratory_report_history
    ON laboratory_report_history(laboratory_report_id, occurred_at DESC);
