CREATE TABLE organization_profile (
    id UUID PRIMARY KEY,
    installation_key VARCHAR(40) NOT NULL UNIQUE,
    commercial_name VARCHAR(180) NOT NULL,
    legal_name VARCHAR(220) NOT NULL,
    document_type VARCHAR(40),
    document_number VARCHAR(40),
    address VARCHAR(400),
    logo_data_url TEXT,
    primary_color VARCHAR(7) NOT NULL,
    secondary_color VARCHAR(7) NOT NULL,
    time_zone VARCHAR(80) NOT NULL,
    legal_text TEXT,
    accreditation_name VARCHAR(200),
    accreditation_code VARCHAR(100),
    accreditation_scope TEXT,
    service_order_prefix VARCHAR(10) NOT NULL,
    work_order_prefix VARCHAR(10) NOT NULL,
    report_prefix VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_organization_primary_color CHECK (primary_color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT chk_organization_secondary_color CHECK (secondary_color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE TABLE organization_contact (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization_profile(id) ON DELETE CASCADE,
    contact_type VARCHAR(20) NOT NULL,
    label VARCHAR(80),
    value VARCHAR(180) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_organization_contact_type CHECK (contact_type IN ('EMAIL', 'PHONE')),
    CONSTRAINT uq_organization_contact UNIQUE (organization_id, contact_type, value)
);

CREATE TABLE organization_authorized_signer (
    organization_id UUID NOT NULL REFERENCES organization_profile(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id),
    display_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (organization_id, user_id)
);

CREATE TABLE organization_profile_history (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization_profile(id) ON DELETE CASCADE,
    action VARCHAR(40) NOT NULL,
    change_note TEXT NOT NULL,
    performed_by VARCHAR(180) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

INSERT INTO organization_profile (
    id,
    installation_key,
    commercial_name,
    legal_name,
    primary_color,
    secondary_color,
    time_zone,
    service_order_prefix,
    work_order_prefix,
    report_prefix,
    created_at,
    updated_at,
    version
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'PRIMARY',
    'Mi laboratorio',
    'Laboratorio',
    '#0F766E',
    '#0F172A',
    'America/Lima',
    'OS',
    'OT',
    'IR',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);

ALTER TABLE client
    ADD COLUMN participant_type VARCHAR(20) NOT NULL DEFAULT 'CLIENT',
    ADD CONSTRAINT chk_client_participant_type
        CHECK (participant_type IN ('CLIENT', 'PRODUCER', 'BOTH'));

ALTER TABLE service_order_sample
    ADD COLUMN producer_id UUID REFERENCES client(id);

CREATE INDEX idx_client_participant_type ON client(participant_type, active);
CREATE INDEX idx_service_order_sample_producer ON service_order_sample(producer_id, created_at DESC);
CREATE INDEX idx_organization_history_date ON organization_profile_history(occurred_at DESC);
