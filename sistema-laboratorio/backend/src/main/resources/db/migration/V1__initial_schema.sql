CREATE TABLE app_role (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE laboratory_area (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    laboratory_area_id UUID REFERENCES laboratory_area(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE app_user_role (
    user_id UUID NOT NULL REFERENCES app_user(id),
    role_id UUID NOT NULL REFERENCES app_role(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE client (
    id UUID PRIMARY KEY,
    document_type VARCHAR(20) NOT NULL,
    document_number VARCHAR(30) NOT NULL,
    legal_name VARCHAR(200) NOT NULL,
    report_name VARCHAR(200),
    email VARCHAR(180),
    address VARCHAR(300),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_client_document UNIQUE (document_type, document_number)
);

CREATE TABLE sample_type (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    laboratory_code_prefix VARCHAR(20) NOT NULL,
    result_mode VARCHAR(30) NOT NULL DEFAULT 'SCALAR',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE measurement_unit (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE laboratory_service (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    laboratory_area_id UUID NOT NULL REFERENCES laboratory_area(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE test_definition (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    result_type VARCHAR(30) NOT NULL DEFAULT 'NUMERIC',
    result_unit_id UUID REFERENCES measurement_unit(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE test_method (
    id UUID PRIMARY KEY,
    test_definition_id UUID NOT NULL REFERENCES test_definition(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    version_label VARCHAR(60),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_test_method_code UNIQUE (test_definition_id, code)
);

CREATE TABLE service_test (
    service_id UUID NOT NULL REFERENCES laboratory_service(id),
    test_definition_id UUID NOT NULL REFERENCES test_definition(id),
    display_order INTEGER NOT NULL DEFAULT 0,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (service_id, test_definition_id)
);

CREATE TABLE service_order (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    client_id UUID NOT NULL REFERENCES client(id),
    laboratory_area_id UUID NOT NULL REFERENCES laboratory_area(id),
    status VARCHAR(30) NOT NULL,
    report_name VARCHAR(200),
    report_email VARCHAR(180),
    report_address VARCHAR(300),
    delivery_method VARCHAR(30) NOT NULL,
    pickup_person VARCHAR(160),
    planned_delivery_at TIMESTAMPTZ NOT NULL,
    proforma_number VARCHAR(80),
    technical_proposal_number VARCHAR(80),
    development_days INTEGER,
    outsourced BOOLEAN NOT NULL DEFAULT FALSE,
    post_analysis_custody VARCHAR(50),
    general_observations TEXT,
    finalized_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE service_order_sample (
    id UUID PRIMARY KEY,
    service_order_id UUID NOT NULL REFERENCES service_order(id),
    commercial_sequence INTEGER NOT NULL,
    client_identification VARCHAR(120),
    product_name VARCHAR(160),
    variety_name VARCHAR(160),
    sample_type_id UUID NOT NULL REFERENCES sample_type(id),
    physical_description TEXT,
    sampling_location VARCHAR(250),
    origin VARCHAR(250),
    sampler_name VARCHAR(160),
    sampled_at TIMESTAMPTZ,
    quantity NUMERIC(14,4),
    measurement_unit_id UUID REFERENCES measurement_unit(id),
    container_description VARCHAR(160),
    batch_number VARCHAR(100),
    production_year INTEGER,
    brand VARCHAR(120),
    origin_certificate VARCHAR(120),
    sanitary_registration VARCHAR(120),
    crop_age VARCHAR(100),
    carrier_count INTEGER,
    bud_count INTEGER,
    counter_sample_in_custody BOOLEAN NOT NULL DEFAULT FALSE,
    observations TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_order_sample_sequence UNIQUE (service_order_id, commercial_sequence)
);

CREATE TABLE service_order_sample_test (
    id UUID PRIMARY KEY,
    service_order_sample_id UUID NOT NULL REFERENCES service_order_sample(id),
    laboratory_service_id UUID NOT NULL REFERENCES laboratory_service(id),
    test_definition_id UUID NOT NULL REFERENCES test_definition(id),
    test_method_id UUID NOT NULL REFERENCES test_method(id),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_sample_requested_test UNIQUE (service_order_sample_id, test_definition_id, test_method_id)
);

CREATE TABLE work_order (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    service_order_id UUID NOT NULL REFERENCES service_order(id),
    laboratory_area_id UUID NOT NULL REFERENCES laboratory_area(id),
    responsible_id UUID NOT NULL REFERENCES app_user(id),
    laboratory_due_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE work_order_sample (
    id UUID PRIMARY KEY,
    work_order_id UUID NOT NULL REFERENCES work_order(id),
    service_order_sample_id UUID NOT NULL REFERENCES service_order_sample(id),
    sample_type_id UUID NOT NULL REFERENCES sample_type(id),
    laboratory_code VARCHAR(40) NOT NULL UNIQUE,
    laboratory_sequence INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_work_order_sample UNIQUE (work_order_id, service_order_sample_id)
);

CREATE TABLE work_order_test (
    id UUID PRIMARY KEY,
    work_order_sample_id UUID NOT NULL REFERENCES work_order_sample(id),
    source_sample_test_id UUID NOT NULL REFERENCES service_order_sample_test(id),
    assigned_analyst_id UUID NOT NULL REFERENCES app_user(id),
    test_method_id UUID NOT NULL REFERENCES test_method(id),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_work_order_test_source UNIQUE (work_order_sample_id, source_sample_test_id)
);

CREATE TABLE result_entry (
    id UUID PRIMARY KEY,
    work_order_test_id UUID NOT NULL UNIQUE REFERENCES work_order_test(id),
    status VARCHAR(30) NOT NULL,
    analyst_observation TEXT,
    recorded_by UUID NOT NULL REFERENCES app_user(id),
    recorded_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE result_value (
    id UUID PRIMARY KEY,
    result_entry_id UUID NOT NULL REFERENCES result_entry(id),
    field_key VARCHAR(80) NOT NULL,
    label VARCHAR(120) NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    value_text VARCHAR(500),
    value_number NUMERIC(18,6),
    measurement_unit_id UUID REFERENCES measurement_unit(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE result_validation (
    id UUID PRIMARY KEY,
    result_entry_id UUID NOT NULL UNIQUE REFERENCES result_entry(id),
    status VARCHAR(30) NOT NULL,
    validator_id UUID NOT NULL REFERENCES app_user(id),
    validated_at TIMESTAMPTZ NOT NULL,
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE result_change_log (
    id UUID PRIMARY KEY,
    result_value_id UUID NOT NULL REFERENCES result_value(id),
    old_value_text VARCHAR(500),
    old_value_number NUMERIC(18,6),
    new_value_text VARCHAR(500),
    new_value_number NUMERIC(18,6),
    reason VARCHAR(500) NOT NULL,
    changed_by UUID NOT NULL REFERENCES app_user(id),
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE sample_code_counter (
    sample_type_id UUID NOT NULL REFERENCES sample_type(id),
    calendar_year INTEGER NOT NULL,
    last_value INTEGER NOT NULL,
    PRIMARY KEY (sample_type_id, calendar_year)
);

CREATE INDEX idx_service_order_status ON service_order(status);
CREATE INDEX idx_service_order_client ON service_order(client_id);
CREATE INDEX idx_service_order_sample_order ON service_order_sample(service_order_id);
CREATE INDEX idx_work_order_status ON work_order(status);
CREATE INDEX idx_work_order_responsible ON work_order(responsible_id);
CREATE INDEX idx_work_order_test_analyst ON work_order_test(assigned_analyst_id);
CREATE INDEX idx_result_entry_status ON result_entry(status);

