ALTER TABLE result_template
    ADD COLUMN sample_type_id UUID REFERENCES sample_type(id),
    ADD COLUMN result_mode VARCHAR(30) NOT NULL DEFAULT 'SCALAR',
    ADD COLUMN minimum_repetitions INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN maximum_repetitions INTEGER,
    ADD COLUMN default_rows INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN maximum_rows INTEGER,
    ADD COLUMN default_columns INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN maximum_columns INTEGER;

ALTER TABLE result_template
    DROP CONSTRAINT uq_result_template_name;

ALTER TABLE result_template
    ADD CONSTRAINT chk_result_template_repetitions
        CHECK (minimum_repetitions > 0
            AND (maximum_repetitions IS NULL OR maximum_repetitions >= minimum_repetitions)),
    ADD CONSTRAINT chk_result_template_rows
        CHECK (default_rows > 0 AND (maximum_rows IS NULL OR maximum_rows >= default_rows)),
    ADD CONSTRAINT chk_result_template_columns
        CHECK (default_columns > 0
            AND (maximum_columns IS NULL OR maximum_columns >= default_columns));

CREATE UNIQUE INDEX uq_result_template_generic_name
    ON result_template(test_definition_id, LOWER(name))
    WHERE sample_type_id IS NULL;

CREATE UNIQUE INDEX uq_result_template_sample_name
    ON result_template(test_definition_id, sample_type_id, LOWER(name))
    WHERE sample_type_id IS NOT NULL;

CREATE INDEX idx_result_template_selection
    ON result_template(test_definition_id, sample_type_id, result_mode, active);

CREATE TABLE result_template_category (
    id UUID PRIMARY KEY,
    result_template_id UUID NOT NULL REFERENCES result_template(id),
    code VARCHAR(50) NOT NULL,
    label VARCHAR(120) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_result_template_category_code UNIQUE (result_template_id, code)
);

CREATE INDEX idx_result_template_category_template
    ON result_template_category(result_template_id, display_order);

ALTER TABLE result_entry
    ADD COLUMN result_template_id UUID REFERENCES result_template(id),
    ADD COLUMN result_mode VARCHAR(30) NOT NULL DEFAULT 'SCALAR';

ALTER TABLE result_value
    ADD COLUMN repetition_index INTEGER NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uq_result_value_field_repetition
    ON result_value(result_entry_id, field_key, repetition_index);

CREATE TABLE result_matrix (
    id UUID PRIMARY KEY,
    result_entry_id UUID NOT NULL UNIQUE REFERENCES result_entry(id),
    row_count INTEGER NOT NULL,
    column_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_result_matrix_dimensions CHECK (row_count > 0 AND column_count > 0)
);

CREATE TABLE result_matrix_column (
    id UUID PRIMARY KEY,
    result_matrix_id UUID NOT NULL REFERENCES result_matrix(id),
    column_index INTEGER NOT NULL,
    carrier_label VARCHAR(120),
    distance NUMERIC(18,6),
    diameter NUMERIC(18,6),
    shape VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_result_matrix_column UNIQUE (result_matrix_id, column_index)
);

CREATE TABLE result_matrix_cell (
    id UUID PRIMARY KEY,
    result_matrix_id UUID NOT NULL REFERENCES result_matrix(id),
    row_index INTEGER NOT NULL,
    column_index INTEGER NOT NULL,
    value_text VARCHAR(500),
    value_number NUMERIC(18,6),
    category_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_result_matrix_cell UNIQUE (result_matrix_id, row_index, column_index)
);

CREATE TABLE result_matrix_change_log (
    id UUID PRIMARY KEY,
    result_matrix_id UUID NOT NULL REFERENCES result_matrix(id),
    target_type VARCHAR(30) NOT NULL,
    row_index INTEGER,
    column_index INTEGER,
    field_name VARCHAR(60) NOT NULL,
    old_value VARCHAR(500),
    new_value VARCHAR(500),
    reason VARCHAR(500) NOT NULL,
    changed_by UUID NOT NULL REFERENCES app_user(id),
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_result_matrix_column_matrix ON result_matrix_column(result_matrix_id);
CREATE INDEX idx_result_matrix_cell_matrix ON result_matrix_cell(result_matrix_id);
CREATE INDEX idx_result_matrix_change_matrix
    ON result_matrix_change_log(result_matrix_id, changed_at DESC);
