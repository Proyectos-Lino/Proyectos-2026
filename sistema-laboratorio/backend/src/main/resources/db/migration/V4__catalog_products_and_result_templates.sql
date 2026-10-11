CREATE TABLE product (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE product_variety (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product(id),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_product_variety_code UNIQUE (product_id, code)
);

CREATE TABLE result_template (
    id UUID PRIMARY KEY,
    test_definition_id UUID NOT NULL REFERENCES test_definition(id),
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_result_template_name UNIQUE (test_definition_id, name)
);

CREATE TABLE result_template_field (
    id UUID PRIMARY KEY,
    result_template_id UUID NOT NULL REFERENCES result_template(id),
    field_key VARCHAR(80) NOT NULL,
    label VARCHAR(120) NOT NULL,
    result_type VARCHAR(30) NOT NULL,
    measurement_unit_id UUID REFERENCES measurement_unit(id),
    required BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_result_template_field_key UNIQUE (result_template_id, field_key)
);

CREATE INDEX idx_product_variety_product ON product_variety(product_id);
CREATE INDEX idx_result_template_test ON result_template(test_definition_id);
CREATE INDEX idx_result_template_field_template ON result_template_field(result_template_id);
