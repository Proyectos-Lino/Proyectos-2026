
INSERT INTO role_permission(role_code,permission)
SELECT role_code,permission FROM unnest(ARRAY['ADMIN','RECEPTION']) role_code
CROSS JOIN unnest(ARRAY['PATIENTS_READ','PATIENTS_WRITE','APPOINTMENTS_READ','APPOINTMENTS_WRITE']) permission
ON CONFLICT DO NOTHING;
INSERT INTO role_permission(role_code,permission)
SELECT role_code,permission FROM unnest(ARRAY['DENTIST','CASHIER']) role_code
CROSS JOIN unnest(ARRAY['PATIENTS_READ','APPOINTMENTS_READ']) permission ON CONFLICT DO NOTHING;

CREATE TABLE patient (
 id UUID PRIMARY KEY, code VARCHAR(30) NOT NULL UNIQUE,
 full_name VARCHAR(160) NOT NULL CHECK(length(trim(full_name))>0),
 birth_date DATE, document_type VARCHAR(20) NOT NULL DEFAULT '',
 document_number VARCHAR(40) NOT NULL DEFAULT '', address VARCHAR(250) NOT NULL DEFAULT '',
 email VARCHAR(160) NOT NULL DEFAULT '', emergency_name VARCHAR(160) NOT NULL DEFAULT '',
 emergency_phone VARCHAR(20) NOT NULL DEFAULT '', notes VARCHAR(2000) NOT NULL DEFAULT '',
 provisional BOOLEAN NOT NULL DEFAULT FALSE, active BOOLEAN NOT NULL DEFAULT TRUE,
 duplicate_key VARCHAR(250), version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(provisional OR birth_date IS NOT NULL),
 CHECK((document_type='' AND document_number='') OR (document_type<>'' AND document_number<>''))
);
CREATE UNIQUE INDEX uq_patient_document ON patient(document_type,document_number) WHERE document_number<>'';
CREATE UNIQUE INDEX uq_patient_identity_contact ON patient(duplicate_key) WHERE duplicate_key IS NOT NULL;
CREATE INDEX idx_patient_name ON patient(active,full_name,id);
CREATE TABLE patient_contact (
 id UUID PRIMARY KEY, patient_id UUID NOT NULL REFERENCES patient(id), phone VARCHAR(20) NOT NULL CHECK(phone ~ '^[+][1-9][0-9]{7,14}$'),
 name VARCHAR(160) NOT NULL, relationship VARCHAR(80) NOT NULL,
 guardian BOOLEAN NOT NULL DEFAULT FALSE, payer BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE(patient_id,phone)
);
CREATE INDEX idx_patient_contact_phone ON patient_contact(phone,patient_id);
CREATE TABLE appointment (
 id UUID PRIMARY KEY, patient_id UUID NOT NULL REFERENCES patient(id), dentist_id UUID NOT NULL REFERENCES dentist(id),
 service_id UUID REFERENCES dental_service(id), service_name VARCHAR(160) NOT NULL,
 dentist_name VARCHAR(120) NOT NULL, duration_minutes INTEGER NOT NULL CHECK(duration_minutes BETWEEN 1 AND 1440),
 starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL,
 gap_minutes INTEGER NOT NULL CHECK(gap_minutes BETWEEN 0 AND 120),
 blocked_until TIMESTAMPTZ NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('RESERVED','CONFIRMED','WAITING','IN_PROGRESS','ATTENDED','CANCELLED','NO_SHOW')),
 origin VARCHAR(16) NOT NULL DEFAULT 'MANUAL' CHECK(origin IN ('MANUAL','WHATSAPP')),
 notes VARCHAR(1000) NOT NULL DEFAULT '', request_key UUID NOT NULL UNIQUE,
 request_fingerprint VARCHAR(64) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(ends_at = starts_at + duration_minutes * INTERVAL '1 minute'),
 CHECK(blocked_until = ends_at + gap_minutes * INTERVAL '1 minute'),
 CONSTRAINT appointment_no_overlap EXCLUDE USING gist (
 dentist_id WITH =, tstzrange(starts_at,blocked_until,'[)') WITH &&
 ) WHERE(status <> 'CANCELLED')
);
CREATE INDEX idx_appointment_calendar ON appointment(dentist_id,starts_at,id);
CREATE INDEX idx_appointment_patient ON appointment(patient_id,starts_at,id);
CREATE TABLE appointment_history (
 id UUID PRIMARY KEY, appointment_id UUID NOT NULL REFERENCES appointment(id),
 action VARCHAR(24) NOT NULL, previous_status VARCHAR(20), status VARCHAR(20) NOT NULL,
 previous_start TIMESTAMPTZ, starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL,
 dentist_id UUID NOT NULL, dentist_name VARCHAR(120) NOT NULL, duration_minutes INTEGER NOT NULL,
 actor_name VARCHAR(120) NOT NULL, reason VARCHAR(500) NOT NULL DEFAULT '',
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_appointment_history ON appointment_history(appointment_id,created_at,id);
CREATE FUNCTION protect_appointment_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Appointment history is immutable'; END $$;
CREATE TRIGGER appointment_history_immutable BEFORE UPDATE OR DELETE ON appointment_history
FOR EACH ROW EXECUTE FUNCTION protect_appointment_history();
