CREATE TABLE whatsapp_conversation (
 id uuid PRIMARY KEY, phone varchar(20) NOT NULL UNIQUE,
 contact_name varchar(120) NOT NULL DEFAULT '', last_message_at timestamptz NOT NULL,
 last_inbound_at timestamptz, last_message_preview varchar(200) NOT NULL DEFAULT ''
);
CREATE TABLE whatsapp_message (
 id uuid PRIMARY KEY, conversation_id uuid NOT NULL REFERENCES whatsapp_conversation(id),
 direction varchar(10) NOT NULL CHECK(direction IN ('INBOUND','OUTBOUND')),
 kind varchar(12) NOT NULL CHECK(kind IN ('TEXT','TEMPLATE','UNSUPPORTED')),
 body varchar(4096) NOT NULL DEFAULT '', provider_sid varchar(34) UNIQUE,
 request_key uuid UNIQUE, template_sid varchar(34), status varchar(16) NOT NULL,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 error_code varchar(20), error_message varchar(300), attempts integer NOT NULL DEFAULT 0,
 next_attempt_at timestamptz NOT NULL,
 CHECK(status IN ('RECEIVED','UNSUPPORTED','QUEUED','SENDING','ACCEPTED','SENT','DELIVERED','READ','FAILED','UNKNOWN')),
 CHECK(attempts >= 0)
);
CREATE INDEX whatsapp_messages_conversation ON whatsapp_message(conversation_id,created_at DESC,id);
CREATE INDEX whatsapp_outbox_pending ON whatsapp_message(next_attempt_at,created_at) WHERE status='QUEUED';
CREATE INDEX whatsapp_conversations_recent ON whatsapp_conversation(last_message_at DESC,id);
CREATE TABLE whatsapp_delivery_event (
 id uuid PRIMARY KEY, message_id uuid NOT NULL REFERENCES whatsapp_message(id),
 provider_sid varchar(34) NOT NULL, status varchar(16) NOT NULL, error_code varchar(20) NOT NULL DEFAULT '',
 received_at timestamptz NOT NULL, UNIQUE(message_id,provider_sid,status,error_code)
);
INSERT INTO role_permission(role_code,permission) SELECT r,p
 FROM unnest(ARRAY['ADMIN','RECEPTION']) r CROSS JOIN unnest(ARRAY['WHATSAPP_READ','WHATSAPP_WRITE']) p;
