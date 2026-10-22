-- Credentials are encrypted by the application; installation starts disconnected.
CREATE TABLE integration_settings (
 id smallint PRIMARY KEY CHECK(id=1), version bigint NOT NULL DEFAULT 0,
 encrypted_payload bytea, updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO integration_settings(id) VALUES(1);
ALTER TABLE agent_run ADD COLUMN integration_revision bigint NOT NULL DEFAULT -1;
ALTER TABLE kapso_message ADD COLUMN integration_revision bigint NOT NULL DEFAULT -1;
CREATE FUNCTION stamp_integration_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 NEW.integration_revision := (SELECT version FROM integration_settings WHERE id=1);
 RETURN NEW;
END $$;
CREATE TRIGGER agent_integration_revision BEFORE INSERT ON agent_run
 FOR EACH ROW EXECUTE FUNCTION stamp_integration_revision();
CREATE TRIGGER kapso_integration_revision BEFORE INSERT ON kapso_message
 FOR EACH ROW EXECUTE FUNCTION stamp_integration_revision();
INSERT INTO role_permission(role_code,permission) VALUES ('ADMIN','INTEGRATIONS_MANAGE');
UPDATE user_account SET auth_version=auth_version+1;
