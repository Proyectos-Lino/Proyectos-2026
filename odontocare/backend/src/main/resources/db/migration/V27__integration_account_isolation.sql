-- A shared Sandbox number is not an account identifier.
ALTER TABLE kapso_conversation ADD COLUMN integration_account uuid NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';
ALTER TABLE kapso_conversation DROP CONSTRAINT kapso_conversation_phone_number_id_phone_key;
ALTER TABLE kapso_conversation ADD UNIQUE(integration_account,phone_number_id,phone);
CREATE INDEX kapso_account_recent ON kapso_conversation(integration_account,phone_number_id,last_message_at DESC,id);
