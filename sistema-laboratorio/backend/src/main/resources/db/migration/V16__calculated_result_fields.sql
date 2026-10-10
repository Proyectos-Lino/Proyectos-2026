ALTER TABLE result_template_field
    ADD COLUMN field_kind VARCHAR(30) NOT NULL DEFAULT 'CAPTURED',
    ADD COLUMN read_only BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN formula VARCHAR(1000),
    ADD COLUMN calculation_scope VARCHAR(30) NOT NULL DEFAULT 'PER_REPETITION',
    ADD COLUMN decimal_precision INTEGER,
    ADD COLUMN information_text VARCHAR(500),
    ADD COLUMN default_value VARCHAR(500),
    ADD CONSTRAINT chk_result_template_field_precision
        CHECK (decimal_precision IS NULL OR decimal_precision BETWEEN 0 AND 10);

ALTER TABLE result_value
    ALTER COLUMN value_number TYPE NUMERIC(24,10),
    ADD COLUMN field_kind VARCHAR(30) NOT NULL DEFAULT 'CAPTURED',
    ADD COLUMN formula_used VARCHAR(1000),
    ADD COLUMN calculation_inputs TEXT;

ALTER TABLE result_change_log
    ALTER COLUMN old_value_number TYPE NUMERIC(24,10),
    ALTER COLUMN new_value_number TYPE NUMERIC(24,10);

CREATE TABLE result_calculation_snapshot (
    id UUID PRIMARY KEY,
    result_entry_id UUID NOT NULL REFERENCES result_entry(id),
    field_key VARCHAR(80) NOT NULL,
    repetition_index INTEGER NOT NULL,
    result_revision INTEGER NOT NULL,
    formula_used VARCHAR(1000) NOT NULL,
    calculation_inputs TEXT NOT NULL,
    calculated_value NUMERIC(24,10) NOT NULL,
    calculated_by UUID NOT NULL REFERENCES app_user(id),
    calculated_by_role VARCHAR(40) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_result_calculation_revision
        UNIQUE (result_entry_id, field_key, repetition_index, result_revision)
);

CREATE INDEX idx_result_calculation_entry
    ON result_calculation_snapshot(result_entry_id, result_revision, field_key);
