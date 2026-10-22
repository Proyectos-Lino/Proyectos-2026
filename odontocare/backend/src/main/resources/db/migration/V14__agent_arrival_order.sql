-- Arrival order must not depend on random UUIDs or timestamps that can be equal.
ALTER TABLE whatsapp_message ADD COLUMN sequence_no bigint GENERATED ALWAYS AS IDENTITY UNIQUE;
ALTER TABLE agent_run ADD COLUMN sequence_no bigint GENERATED ALWAYS AS IDENTITY UNIQUE;
CREATE INDEX whatsapp_inbound_order ON whatsapp_message(conversation_id,sequence_no DESC) WHERE direction='INBOUND';
CREATE INDEX agent_pending_order ON agent_run(sequence_no) WHERE state IN ('QUEUED','PROCESSING');
