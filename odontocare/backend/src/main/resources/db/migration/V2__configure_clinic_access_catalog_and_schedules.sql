ALTER TABLE installation_profile
    ADD COLUMN legal_name VARCHAR(160) NOT NULL DEFAULT '',
    ADD COLUMN address VARCHAR(250) NOT NULL DEFAULT '',
    ADD COLUMN phone VARCHAR(30) NOT NULL DEFAULT '',
    ADD COLUMN email VARCHAR(160) NOT NULL DEFAULT '',
    ADD COLUMN brand_color VARCHAR(7) NOT NULL DEFAULT '#215e4d',
    ADD COLUMN accent_color VARCHAR(7) NOT NULL DEFAULT '#edf2e9',
    ADD COLUMN date_format VARCHAR(16) NOT NULL DEFAULT 'DMY' CHECK (date_format IN ('DMY','MDY','YMD')),
    ADD COLUMN document_header VARCHAR(1000) NOT NULL DEFAULT '',
    ADD COLUMN document_footer VARCHAR(1000) NOT NULL DEFAULT '',
    ADD COLUMN appointment_instructions VARCHAR(1000) NOT NULL DEFAULT '',
    ADD COLUMN patient_prefix VARCHAR(10) NOT NULL DEFAULT 'PAC',
    ADD COLUMN patient_next_number INTEGER NOT NULL DEFAULT 1 CHECK (patient_next_number > 0),
    ADD COLUMN receipt_prefix VARCHAR(10) NOT NULL DEFAULT 'REC',
    ADD COLUMN receipt_next_number INTEGER NOT NULL DEFAULT 1 CHECK (receipt_next_number > 0),
    ADD COLUMN budget_prefix VARCHAR(10) NOT NULL DEFAULT 'PRE',
    ADD COLUMN budget_next_number INTEGER NOT NULL DEFAULT 1 CHECK (budget_next_number > 0),
    ADD COLUMN minimum_lead_minutes INTEGER NOT NULL DEFAULT 120 CHECK (minimum_lead_minutes BETWEEN 0 AND 43800),
    ADD COLUMN appointment_gap_minutes INTEGER NOT NULL DEFAULT 0 CHECK (appointment_gap_minutes BETWEEN 0 AND 120),
    ADD COLUMN logo_revision INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE installation_logo (
    id SMALLINT PRIMARY KEY REFERENCES installation_profile(id),
    content_type VARCHAR(30) NOT NULL CHECK (content_type IN ('image/png','image/jpeg')),
    content BYTEA NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 2097152)
);

CREATE TABLE role_definition (
    code VARCHAR(24) PRIMARY KEY CHECK (code IN ('ADMIN','DENTIST','RECEPTION','CASHIER')),
    name VARCHAR(80) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE role_permission (
    role_code VARCHAR(24) NOT NULL REFERENCES role_definition(code),
    permission VARCHAR(40) NOT NULL,
    PRIMARY KEY (role_code, permission)
);
INSERT INTO role_definition(code,name) VALUES
('ADMIN','Administrador'),('DENTIST','Odontólogo'),('RECEPTION','Recepción'),('CASHIER','Caja');
INSERT INTO role_permission(role_code,permission)
SELECT 'ADMIN', unnest(ARRAY['SETTINGS_READ','SETTINGS_WRITE','USERS_READ','USERS_WRITE','ROLES_READ','ROLES_WRITE','DENTISTS_READ','DENTISTS_WRITE','SERVICES_READ','SERVICES_WRITE','SCHEDULES_READ','SCHEDULES_WRITE','AUDIT_READ']);
INSERT INTO role_permission(role_code,permission)
SELECT role_code, permission FROM unnest(ARRAY['DENTIST','RECEPTION','CASHIER']) AS role_code
CROSS JOIN unnest(ARRAY['SETTINGS_READ','DENTISTS_READ','SERVICES_READ','SCHEDULES_READ']) AS permission;

CREATE TABLE user_account (
    id UUID PRIMARY KEY,
    username VARCHAR(60) NOT NULL UNIQUE CHECK (username ~ '^[a-z0-9._-]{3,60}$'),
    display_name VARCHAR(120) NOT NULL CHECK (length(trim(display_name)) > 0),
    email VARCHAR(160) NOT NULL DEFAULT '',
    password_hash VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    auth_version BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE user_role (
    user_id UUID NOT NULL REFERENCES user_account(id),
    role_code VARCHAR(24) NOT NULL REFERENCES role_definition(code),
    PRIMARY KEY (user_id,role_code)
);
CREATE INDEX idx_user_account_active_name ON user_account(active,display_name,id);

CREATE TABLE service_category (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (length(trim(name)) > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_service_category_name ON service_category(lower(name));
CREATE TABLE dental_service (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    category_id UUID NOT NULL REFERENCES service_category(id),
    price NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes BETWEEN 1 AND 1440),
    description VARCHAR(1000) NOT NULL DEFAULT '',
    bookable_by_agent BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_dental_service_name ON dental_service(lower(name));
CREATE INDEX idx_dental_service_catalog ON dental_service(active,category_id,name,id);
CREATE TABLE dentist (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES user_account(id),
    full_name VARCHAR(120) NOT NULL CHECK (length(trim(full_name)) > 0),
    license_number VARCHAR(40) NOT NULL,
    specialty VARCHAR(120) NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_dentist_license ON dentist(lower(license_number));
CREATE INDEX idx_dentist_active_name ON dentist(active,full_name,id);
CREATE TABLE dentist_service (
    dentist_id UUID NOT NULL REFERENCES dentist(id),
    service_id UUID NOT NULL REFERENCES dental_service(id),
    PRIMARY KEY (dentist_id,service_id)
);
CREATE INDEX idx_dentist_service_service ON dentist_service(service_id,dentist_id);

CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE weekly_period (
    id UUID PRIMARY KEY,
    dentist_id UUID NOT NULL REFERENCES dentist(id),
    day_of_week INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('WORK','BREAK')),
    start_minute INTEGER NOT NULL CHECK (start_minute BETWEEN 0 AND 1439),
    end_minute INTEGER NOT NULL CHECK (end_minute BETWEEN 1 AND 1440 AND end_minute > start_minute),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT weekly_period_no_overlap EXCLUDE USING gist (
        dentist_id WITH =, day_of_week WITH =, kind WITH =,
        int4range(start_minute,end_minute,'[)') WITH &&
    ) WHERE (active)
);
CREATE INDEX idx_weekly_period_dentist_day ON weekly_period(dentist_id,day_of_week,active);
CREATE TABLE schedule_exception (
    id UUID PRIMARY KEY,
    dentist_id UUID REFERENCES dentist(id),
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('HOLIDAY','ABSENCE')),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL CHECK (end_date >= start_date),
    start_minute INTEGER,
    end_minute INTEGER,
    reason VARCHAR(200) NOT NULL CHECK (length(trim(reason)) > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (kind != 'ABSENCE' OR dentist_id IS NOT NULL),
    CHECK ((start_minute IS NULL AND end_minute IS NULL) OR
           (start_date = end_date AND start_minute BETWEEN 0 AND 1439 AND end_minute BETWEEN 1 AND 1440 AND end_minute > start_minute))
);
CREATE INDEX idx_schedule_exception_dates ON schedule_exception(active,start_date,end_date,dentist_id);
CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES user_account(id),
    actor_name VARCHAR(120) NOT NULL,
    action VARCHAR(40) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    request_id VARCHAR(36),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_event_time ON audit_event(occurred_at DESC,id);
CREATE INDEX idx_audit_event_entity ON audit_event(entity_type,entity_id,occurred_at DESC);
CREATE FUNCTION protect_audit_event() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit events are append-only';
END;
$$;
CREATE TRIGGER audit_event_immutable BEFORE UPDATE OR DELETE ON audit_event
FOR EACH ROW EXECUTE FUNCTION protect_audit_event();
