-- Separate experimental connector: the existing Twilio tables and constraints are preserved.
CREATE TABLE kapso_conversation (
 id uuid PRIMARY KEY, phone_number_id varchar(30) NOT NULL, phone varchar(20) NOT NULL,
 contact_name varchar(120) NOT NULL DEFAULT '', last_message_at timestamptz NOT NULL,
 last_inbound_at timestamptz, last_message_preview varchar(200) NOT NULL DEFAULT '',
 UNIQUE(phone_number_id,phone)
);
CREATE TABLE kapso_message (
 id uuid PRIMARY KEY, conversation_id uuid NOT NULL REFERENCES kapso_conversation(id),
 direction varchar(10) NOT NULL CHECK(direction IN ('INBOUND','OUTBOUND')),
 kind varchar(12) NOT NULL CHECK(kind IN ('TEXT','UNSUPPORTED')),
 body varchar(4096) NOT NULL DEFAULT '', provider_sid varchar(512) UNIQUE, request_key uuid UNIQUE,
 status varchar(16) NOT NULL CHECK(status IN ('RECEIVED','UNSUPPORTED','QUEUED','SENDING','ACCEPTED','SENT','DELIVERED','READ','FAILED','UNKNOWN')),
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, next_attempt_at timestamptz NOT NULL,
 error_code varchar(80), error_message varchar(300), attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0)
);
-- Verified delivery receipts can precede the HTTP send response; keep them until it is linked.
CREATE TABLE kapso_webhook_event (
 delivery_key varchar(160) PRIMARY KEY, payload_hash char(64) NOT NULL,
 event varchar(80) NOT NULL, phone_number_id varchar(30) NOT NULL,
 message_reference varchar(512) NOT NULL, recipient_phone varchar(20) NOT NULL,
 state varchar(16) NOT NULL, error_code varchar(80) NOT NULL DEFAULT '', received_at timestamptz NOT NULL
);
CREATE INDEX kapso_conversations_recent ON kapso_conversation(phone_number_id,last_message_at DESC,id);
CREATE INDEX kapso_messages_conversation ON kapso_message(conversation_id,created_at DESC,id);
CREATE INDEX kapso_pending ON kapso_message(next_attempt_at,created_at) WHERE status='QUEUED';
CREATE INDEX kapso_delivery_reference ON kapso_webhook_event(phone_number_id,message_reference,received_at);
