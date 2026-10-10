ALTER TABLE result_entry
    ADD COLUMN result_revision INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN correction_request_reason VARCHAR(500),
    ADD COLUMN correction_requested_by UUID REFERENCES app_user(id),
    ADD COLUMN correction_requested_by_role VARCHAR(40),
    ADD COLUMN correction_requested_at TIMESTAMPTZ;

ALTER TABLE result_validation
    ADD COLUMN validator_role VARCHAR(40) NOT NULL DEFAULT 'VALIDATOR',
    ADD COLUMN result_revision INTEGER NOT NULL DEFAULT 1;

ALTER TABLE result_change_log
    ADD COLUMN changed_by_role VARCHAR(40) NOT NULL DEFAULT 'ANALYST';

ALTER TABLE result_matrix_change_log
    ADD COLUMN changed_by_role VARCHAR(40) NOT NULL DEFAULT 'ANALYST';

CREATE TABLE result_review_history (
    id UUID PRIMARY KEY,
    result_entry_id UUID NOT NULL REFERENCES result_entry(id),
    action VARCHAR(40) NOT NULL,
    previous_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    result_revision INTEGER NOT NULL,
    reason VARCHAR(500),
    comment VARCHAR(1000),
    performed_by UUID NOT NULL REFERENCES app_user(id),
    performed_by_role VARCHAR(40) NOT NULL,
    performed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_result_review_history_entry
    ON result_review_history(result_entry_id, performed_at DESC);

CREATE INDEX idx_result_entry_review_queue
    ON result_entry(status, recorded_by, recorded_at);
