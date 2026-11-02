ALTER TABLE result_validation
    ADD CONSTRAINT uq_result_validation_revision UNIQUE (result_entry_id, result_revision);

ALTER TABLE result_entry
    ADD COLUMN last_recorded_by UUID REFERENCES app_user(id),
    ADD COLUMN last_recorded_at TIMESTAMPTZ;
