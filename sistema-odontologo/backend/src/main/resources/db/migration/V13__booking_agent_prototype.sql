ALTER TABLE whatsapp_message ADD COLUMN source varchar(16) NOT NULL DEFAULT 'TWILIO'
 CHECK(source IN ('TWILIO','APP_TEST'));
CREATE TABLE agent_run (
 id uuid PRIMARY KEY, message_id uuid NOT NULL UNIQUE REFERENCES whatsapp_message(id),
 conversation_id uuid NOT NULL REFERENCES whatsapp_conversation(id),
 state varchar(20) NOT NULL CHECK(state IN ('QUEUED','PROCESSING','COMPLETED','FAILED')),
 model varchar(100) NOT NULL, response_text varchar(6000) NOT NULL DEFAULT '',
 error_code varchar(40), error_message varchar(500), attempts integer NOT NULL DEFAULT 0,
 input_tokens integer NOT NULL DEFAULT 0, output_tokens integer NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE INDEX agent_pending ON agent_run(created_at,id) WHERE state='QUEUED';
CREATE INDEX agent_conversation_runs ON agent_run(conversation_id,created_at DESC,id);
CREATE TABLE agent_step (
 id uuid PRIMARY KEY, run_id uuid NOT NULL REFERENCES agent_run(id), ordinal integer NOT NULL,
 kind varchar(12) NOT NULL CHECK(kind IN ('MODEL','TOOL','BOOKING')),
 name varchar(100) NOT NULL, arguments_json jsonb NOT NULL DEFAULT '{}',
 result_json jsonb NOT NULL DEFAULT '{}', state varchar(12) NOT NULL,
 created_at timestamptz NOT NULL, UNIQUE(run_id,ordinal)
);
CREATE TABLE agent_slot (
 id uuid PRIMARY KEY, run_id uuid NOT NULL REFERENCES agent_run(id),
 conversation_id uuid NOT NULL REFERENCES whatsapp_conversation(id),
 dentist_id uuid NOT NULL REFERENCES dentist(id), service_id uuid NOT NULL REFERENCES dental_service(id),
 local_start timestamp NOT NULL, duration_minutes integer NOT NULL, time_zone varchar(80) NOT NULL,
 expires_at timestamptz NOT NULL
);
CREATE INDEX agent_slots_conversation ON agent_slot(conversation_id,expires_at);
CREATE TABLE agent_proposal (
 id uuid PRIMARY KEY, run_id uuid NOT NULL UNIQUE REFERENCES agent_run(id),
 conversation_id uuid NOT NULL REFERENCES whatsapp_conversation(id),
 slot_id uuid NOT NULL REFERENCES agent_slot(id), patient_id uuid REFERENCES patient(id),
 patient_name varchar(160) NOT NULL, summary varchar(1200) NOT NULL,
 confirmation_code varchar(8) NOT NULL UNIQUE,
 state varchar(20) NOT NULL CHECK(state IN ('PENDING','CONFIRMED','SUPERSEDED','EXPIRED','CONFLICT')),
 appointment_id uuid REFERENCES appointment(id), confirmation_message_id uuid REFERENCES whatsapp_message(id),
 created_at timestamptz NOT NULL, expires_at timestamptz NOT NULL,
 CHECK(state <> 'CONFIRMED' OR (appointment_id IS NOT NULL AND confirmation_message_id IS NOT NULL))
);
CREATE UNIQUE INDEX agent_one_pending_proposal ON agent_proposal(conversation_id) WHERE state='PENDING';
ALTER TABLE appointment DROP CONSTRAINT appointment_origin_check;
ALTER TABLE appointment ADD CONSTRAINT appointment_origin_check CHECK(origin IN ('MANUAL','WHATSAPP','AI_TEST'));
INSERT INTO role_permission(role_code,permission) VALUES ('ADMIN','AGENT_TEST_WRITE');
