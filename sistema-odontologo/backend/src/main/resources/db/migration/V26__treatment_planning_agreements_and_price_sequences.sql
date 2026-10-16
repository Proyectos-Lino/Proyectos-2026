ALTER TABLE treatment_item ADD COLUMN duration_minutes integer NOT NULL DEFAULT 30 CHECK (duration_minutes BETWEEN 1 AND 1440);
ALTER TABLE treatment_item ADD COLUMN scheduling jsonb NOT NULL DEFAULT '{"mode":"NONE","dates":[]}';
ALTER TABLE treatment_plan ADD COLUMN payment_agreement jsonb NOT NULL DEFAULT '{"initialAmount":0,"installments":[]}';
ALTER TABLE treatment_plan ADD COLUMN initial_payment_id uuid REFERENCES money_movement(id);

CREATE TABLE treatment_scheduled_session (
  id uuid PRIMARY KEY,
  plan_id uuid NOT NULL REFERENCES treatment_plan(id),
  item_id uuid NOT NULL REFERENCES treatment_item(id) ON DELETE CASCADE,
  session_number integer NOT NULL CHECK (session_number BETWEEN 1 AND 100),
  planned_on date,
  duration_minutes integer NOT NULL CHECK (duration_minutes BETWEEN 1 AND 1440),
  status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SCHEDULED','COMPLETED','CANCELLED')),
  appointment_id uuid REFERENCES appointment(id),
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (item_id,session_number),
  CHECK ((status='PENDING' AND appointment_id IS NULL) OR status<>'PENDING')
);
CREATE INDEX treatment_sessions_calendar_idx ON treatment_scheduled_session(planned_on,status,plan_id);
CREATE UNIQUE INDEX treatment_sessions_appointment_idx ON treatment_scheduled_session(appointment_id) WHERE appointment_id IS NOT NULL;

ALTER TABLE appointment ADD COLUMN plan_item_id uuid REFERENCES treatment_item(id);
ALTER TABLE appointment ADD COLUMN scheduled_session_id uuid REFERENCES treatment_scheduled_session(id);
ALTER TABLE appointment ADD COLUMN agreed_price numeric(10,2) CHECK (agreed_price >= 0);
ALTER TABLE appointment ADD COLUMN administrative boolean NOT NULL DEFAULT false;
ALTER TABLE appointment ADD COLUMN treatment_label varchar(220);
ALTER TABLE appointment ADD COLUMN treatment_session_number integer;
ALTER TABLE appointment ALTER COLUMN service_name TYPE varchar(300);
UPDATE appointment SET administrative=true WHERE service_id IS NULL;
CREATE UNIQUE INDEX appointment_active_treatment_session_idx ON appointment(scheduled_session_id)
  WHERE scheduled_session_id IS NOT NULL AND status NOT IN ('CANCELLED','NO_SHOW');

CREATE TABLE treatment_proforma (
  id uuid PRIMARY KEY,
  plan_id uuid NOT NULL REFERENCES treatment_plan(id),
  plan_version bigint NOT NULL,
  plan_status varchar(20) NOT NULL,
  code varchar(80) NOT NULL,
  created_by varchar(120) NOT NULL,
  byte_size integer NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(plan_id,plan_version)
);
CREATE TABLE treatment_proforma_content (
  document_id uuid PRIMARY KEY REFERENCES treatment_proforma(id) ON DELETE CASCADE,
  content bytea NOT NULL
);

ALTER TABLE agent_policy ADD COLUMN max_price_services integer NOT NULL DEFAULT 3 CHECK(max_price_services BETWEEN 1 AND 20);
CREATE TABLE agent_price_sequence (
  conversation_id uuid NOT NULL REFERENCES agent_conversation_source(id),
  source varchar(16) NOT NULL,
  service_ids uuid[] NOT NULL DEFAULT '{}',
  last_query_at timestamptz,
  warning_count integer NOT NULL DEFAULT 0,
  last_denied_run_id uuid,
  PRIMARY KEY(conversation_id,source)
);
