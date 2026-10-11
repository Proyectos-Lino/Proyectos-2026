ALTER TABLE service_order
    ADD COLUMN status_before_correction VARCHAR(30),
    ADD COLUMN correction_reason VARCHAR(1000),
    ADD COLUMN correction_requested_by VARCHAR(180),
    ADD COLUMN correction_requested_at TIMESTAMPTZ,
    ADD COLUMN correction_submitted_by VARCHAR(180),
    ADD COLUMN correction_submitted_at TIMESTAMPTZ,
    ADD COLUMN correction_reviewed_by VARCHAR(180),
    ADD COLUMN correction_reviewed_at TIMESTAMPTZ,
    ADD COLUMN correction_impact_summary VARCHAR(2000),
    ADD COLUMN correction_requires_technical_work BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE service_order_sample
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN archive_reason VARCHAR(1000);

ALTER TABLE service_order_sample
    DROP CONSTRAINT IF EXISTS uq_order_sample_sequence;

CREATE UNIQUE INDEX uq_order_sample_active_sequence
    ON service_order_sample(service_order_id, commercial_sequence)
    WHERE active;

ALTER TABLE service_order_sample_test
    DROP CONSTRAINT IF EXISTS uq_sample_requested_test;

CREATE UNIQUE INDEX uq_sample_requested_test_active
    ON service_order_sample_test(service_order_sample_id, test_definition_id, test_method_id)
    WHERE status <> 'CANCELLED';

ALTER TABLE work_order
    ADD COLUMN status_before_correction VARCHAR(30);

ALTER TABLE work_order_test
    ADD COLUMN invalidated_at TIMESTAMPTZ,
    ADD COLUMN invalidated_by VARCHAR(180),
    ADD COLUMN invalidation_reason VARCHAR(1000);

ALTER TABLE result_entry
    ADD COLUMN invalidated_at TIMESTAMPTZ,
    ADD COLUMN invalidated_by VARCHAR(180),
    ADD COLUMN invalidation_reason VARCHAR(1000);

ALTER TABLE service_order_document
    DROP CONSTRAINT IF EXISTS service_order_document_service_order_id_key;

ALTER TABLE service_order_document
    ADD CONSTRAINT uq_service_order_document_version UNIQUE (service_order_id, version);

ALTER TABLE work_order_document
    DROP CONSTRAINT IF EXISTS work_order_document_work_order_id_key;

ALTER TABLE work_order_document
    ADD CONSTRAINT uq_work_order_document_version UNIQUE (work_order_id, version);

CREATE INDEX idx_service_order_correction_status
    ON service_order(status, correction_requested_at DESC);

CREATE INDEX idx_service_order_sample_active
    ON service_order_sample(service_order_id, active, commercial_sequence);
