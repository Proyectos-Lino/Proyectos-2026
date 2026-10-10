ALTER TABLE result_matrix
    ADD COLUMN execution_status VARCHAR(30),
    ADD COLUMN declared_row_count INTEGER,
    ADD COLUMN declared_column_count INTEGER,
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN last_modified_at TIMESTAMPTZ,
    ADD COLUMN last_modified_by UUID REFERENCES app_user(id);

UPDATE result_matrix matrix
SET execution_status = 'COMPLETED',
    declared_row_count = matrix.row_count,
    declared_column_count = matrix.column_count,
    started_at = matrix.created_at,
    completed_at = matrix.updated_at,
    last_modified_at = matrix.updated_at,
    last_modified_by = entry.recorded_by
FROM result_entry entry
WHERE entry.id = matrix.result_entry_id;

ALTER TABLE result_matrix
    ALTER COLUMN execution_status SET NOT NULL,
    ALTER COLUMN declared_row_count SET NOT NULL,
    ALTER COLUMN declared_column_count SET NOT NULL,
    ALTER COLUMN started_at SET NOT NULL,
    ALTER COLUMN last_modified_at SET NOT NULL,
    ALTER COLUMN last_modified_by SET NOT NULL,
    ADD CONSTRAINT chk_result_matrix_execution_status
        CHECK (execution_status IN ('IN_PROGRESS', 'COMPLETED')),
    ADD CONSTRAINT chk_result_matrix_declared_dimensions
        CHECK (declared_row_count > 0 AND declared_column_count > 0),
    ADD CONSTRAINT chk_result_matrix_completion
        CHECK ((execution_status = 'COMPLETED' AND completed_at IS NOT NULL)
            OR (execution_status = 'IN_PROGRESS' AND completed_at IS NULL));

CREATE INDEX idx_result_matrix_execution_status
    ON result_matrix(execution_status, last_modified_at DESC);
