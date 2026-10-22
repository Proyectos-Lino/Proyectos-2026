-- Link the agent to either channel with real foreign keys; do not duplicate message bodies.
ALTER TABLE kapso_message ADD COLUMN source varchar(16) NOT NULL DEFAULT 'KAPSO'
 CHECK(source IN ('KAPSO','APP_TEST','AGENT'));
CREATE TABLE agent_conversation_source (
 id uuid PRIMARY KEY, provider varchar(12) NOT NULL CHECK(provider IN ('TWILIO','KAPSO')),
 whatsapp_id uuid UNIQUE REFERENCES whatsapp_conversation(id),
 kapso_id uuid UNIQUE REFERENCES kapso_conversation(id), UNIQUE(id,provider),
 CHECK((provider='TWILIO' AND whatsapp_id IS NOT NULL AND whatsapp_id=id AND kapso_id IS NULL)
    OR (provider='KAPSO' AND kapso_id IS NOT NULL AND kapso_id=id AND whatsapp_id IS NULL))
);
CREATE TABLE agent_message_source (
 id uuid PRIMARY KEY, conversation_id uuid NOT NULL, provider varchar(12) NOT NULL,
 whatsapp_id uuid UNIQUE REFERENCES whatsapp_message(id), kapso_id uuid UNIQUE REFERENCES kapso_message(id),
 sequence_no bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
 FOREIGN KEY(conversation_id,provider) REFERENCES agent_conversation_source(id,provider),
 CHECK((provider='TWILIO' AND whatsapp_id IS NOT NULL AND whatsapp_id=id AND kapso_id IS NULL)
    OR (provider='KAPSO' AND kapso_id IS NOT NULL AND kapso_id=id AND whatsapp_id IS NULL))
);
INSERT INTO agent_conversation_source(id,provider,whatsapp_id) SELECT id,'TWILIO',id FROM whatsapp_conversation;
INSERT INTO agent_conversation_source(id,provider,kapso_id) SELECT id,'KAPSO',id FROM kapso_conversation;
INSERT INTO agent_message_source(id,conversation_id,provider,whatsapp_id)
 SELECT id,conversation_id,'TWILIO',id FROM whatsapp_message ORDER BY sequence_no;
INSERT INTO agent_message_source(id,conversation_id,provider,kapso_id)
 SELECT id,conversation_id,'KAPSO',id FROM kapso_message ORDER BY created_at,id;
ALTER TABLE agent_run DROP CONSTRAINT agent_run_message_id_fkey;
ALTER TABLE agent_run DROP CONSTRAINT agent_run_conversation_id_fkey;
ALTER TABLE agent_slot DROP CONSTRAINT agent_slot_conversation_id_fkey;
ALTER TABLE agent_proposal DROP CONSTRAINT agent_proposal_conversation_id_fkey;
ALTER TABLE agent_proposal DROP CONSTRAINT agent_proposal_confirmation_message_id_fkey;
ALTER TABLE agent_run ADD FOREIGN KEY(message_id) REFERENCES agent_message_source(id);
ALTER TABLE agent_run ADD FOREIGN KEY(conversation_id) REFERENCES agent_conversation_source(id);
ALTER TABLE agent_slot ADD FOREIGN KEY(conversation_id) REFERENCES agent_conversation_source(id);
ALTER TABLE agent_proposal ADD FOREIGN KEY(conversation_id) REFERENCES agent_conversation_source(id);
ALTER TABLE agent_proposal ADD FOREIGN KEY(confirmation_message_id) REFERENCES agent_message_source(id);
ALTER TABLE agent_run ADD COLUMN reply_message_id uuid UNIQUE REFERENCES kapso_message(id);
ALTER TABLE agent_run ADD COLUMN next_attempt_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE agent_run ADD COLUMN grouped_into_run_id uuid REFERENCES agent_run(id);
ALTER TABLE agent_run DROP CONSTRAINT agent_run_state_check;
ALTER TABLE agent_run ADD CONSTRAINT agent_run_state_check CHECK(state IN ('QUEUED','PROCESSING','COMPLETED','FAILED','GROUPED'));
CREATE INDEX agent_sources_recent ON agent_message_source(conversation_id,sequence_no DESC);
CREATE VIEW agent_inbox_message AS
 SELECT s.id,s.conversation_id,s.provider,s.sequence_no,
 coalesce(w.body,k.body) body,coalesce(w.direction,k.direction) direction,
 coalesce(w.kind,k.kind) kind,coalesce(w.created_at,k.created_at) created_at,
 coalesce(w.source,k.source) source
 FROM agent_message_source s LEFT JOIN whatsapp_message w ON w.id=s.whatsapp_id
 LEFT JOIN kapso_message k ON k.id=s.kapso_id;
