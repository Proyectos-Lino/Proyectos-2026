CREATE TABLE agent_policy (
 id smallint PRIMARY KEY CHECK(id=1), version bigint NOT NULL DEFAULT 0,
 enabled boolean NOT NULL DEFAULT true, schedule jsonb NOT NULL DEFAULT '[]',
 change_lead_minutes integer NOT NULL DEFAULT 0 CHECK(change_lead_minutes BETWEEN 0 AND 43200),
 allow_reschedule boolean NOT NULL DEFAULT true, allow_cancel boolean NOT NULL DEFAULT true,
 handoff_text varchar(900) NOT NULL DEFAULT 'Voy a derivar tu consulta a recepción para que una persona pueda ayudarte. El asistente queda pausado.',
 clinical_text varchar(900) NOT NULL DEFAULT 'Esta consulta necesita atención del profesional. No puedo evaluar síntomas ni acceder a tu expediente. Contacta directamente al consultorio; voy a avisar a recepción.',
 closed_text varchar(900) NOT NULL DEFAULT 'El asistente está fuera de su horario. Tu mensaje queda pendiente para recepción.',
 failure_text varchar(900) NOT NULL DEFAULT 'No pude completar la consulta. La dejo pendiente para recepción. Revisa la agenda antes de repetir una operación.'
);
INSERT INTO agent_policy(id) VALUES(1);
CREATE TABLE agent_supervision (
 conversation_id uuid PRIMARY KEY REFERENCES agent_conversation_source(id),
 mode varchar(12) NOT NULL DEFAULT 'AUTO' CHECK(mode IN ('AUTO','HUMAN','HANDOFF','CLOSED')),
 generation bigint NOT NULL DEFAULT 0, assigned_user_id uuid REFERENCES user_account(id),
 reason varchar(500) NOT NULL DEFAULT '', updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE agent_request_context (
 conversation_id uuid NOT NULL REFERENCES agent_conversation_source(id), source varchar(12) NOT NULL CHECK(source IN ('KAPSO','TWILIO','APP_TEST')),
 state varchar(30) NOT NULL DEFAULT 'INFORMATION_PENDING' CHECK(state IN ('INFORMATION_PENDING','OPTIONS_OFFERED','CONFIRMATION_PENDING','COMPLETED','EXPIRED','REFERRED')),
 summary varchar(1200) NOT NULL DEFAULT '', patient_id uuid REFERENCES patient(id), patient_name varchar(160) NOT NULL DEFAULT '',
 relationship varchar(12) CHECK(relationship IN ('SELF','GUARDIAN')), verified_at timestamptz,
 appointment_id uuid REFERENCES appointment(id), updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(conversation_id,source)
);
ALTER TABLE agent_run ADD COLUMN control_generation bigint NOT NULL DEFAULT 0;
ALTER TABLE agent_run ADD COLUMN flow_version varchar(60) NOT NULL DEFAULT 'booking-v6';
ALTER TABLE agent_run ADD COLUMN provider varchar(60) NOT NULL DEFAULT 'groq';
ALTER TABLE agent_run ADD COLUMN operational_result varchar(30) NOT NULL DEFAULT 'PENDING';
ALTER TABLE agent_run DROP CONSTRAINT agent_run_state_check;
ALTER TABLE agent_run ADD CHECK(state IN ('QUEUED','PROCESSING','COMPLETED','FAILED','GROUPED','PAUSED'));
CREATE TABLE agent_change_proposal (
 id uuid PRIMARY KEY, run_id uuid NOT NULL UNIQUE REFERENCES agent_run(id), conversation_id uuid NOT NULL REFERENCES agent_conversation_source(id),
 source varchar(12) NOT NULL CHECK(source IN ('KAPSO','TWILIO','APP_TEST')), action varchar(12) NOT NULL CHECK(action IN ('RESCHEDULE','CANCEL')),
 appointment_id uuid NOT NULL REFERENCES appointment(id), patient_id uuid NOT NULL REFERENCES patient(id),
 appointment_version bigint NOT NULL, slot_id uuid REFERENCES agent_slot(id), reason varchar(500) NOT NULL,
 summary varchar(1200) NOT NULL, confirmation_code varchar(8) NOT NULL UNIQUE,
 state varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','CONFIRMED','SUPERSEDED','EXPIRED','CONFLICT')),
 confirmation_message_id uuid REFERENCES agent_message_source(id), created_at timestamptz NOT NULL, expires_at timestamptz NOT NULL,
 CHECK(action <> 'RESCHEDULE' OR slot_id IS NOT NULL), CHECK(state <> 'CONFIRMED' OR confirmation_message_id IS NOT NULL)
);
CREATE UNIQUE INDEX agent_pending_change ON agent_change_proposal(conversation_id,source) WHERE state='PENDING';
CREATE TABLE agent_appointment_reference (
 id uuid PRIMARY KEY, conversation_id uuid NOT NULL REFERENCES agent_conversation_source(id), source varchar(12) NOT NULL,
 patient_id uuid NOT NULL REFERENCES patient(id), appointment_id uuid NOT NULL REFERENCES appointment(id), expires_at timestamptz NOT NULL
);
CREATE INDEX agent_reference_lookup ON agent_appointment_reference(conversation_id,source,expires_at);
INSERT INTO role_permission(role_code,permission) VALUES ('ADMIN','AGENT_CONTROL_WRITE'),('RECEPTION','AGENT_CONTROL_WRITE');
