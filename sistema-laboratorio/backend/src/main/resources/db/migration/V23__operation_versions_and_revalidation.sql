CREATE TABLE operation_version (
    id UUID PRIMARY KEY,
    operation_type VARCHAR(30) NOT NULL,
    operation_id UUID NOT NULL,
    revision INTEGER NOT NULL,
    action VARCHAR(80) NOT NULL,
    reason VARCHAR(2000),
    performed_by VARCHAR(180) NOT NULL,
    performed_by_role VARCHAR(150) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    snapshot_content TEXT NOT NULL,
    UNIQUE (operation_type, operation_id, revision)
);

ALTER TABLE result_validation DROP CONSTRAINT result_validation_result_entry_id_key;
CREATE INDEX idx_validation_entry_date ON result_validation(result_entry_id, validated_at DESC);
