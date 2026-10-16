-- A preview and a real WhatsApp request may share a phone without displacing each other.
ALTER TABLE agent_proposal ADD COLUMN source varchar(12);
UPDATE agent_proposal p SET source=m.source
FROM agent_run r JOIN agent_inbox_message m ON m.id=r.message_id
WHERE r.id=p.run_id;
ALTER TABLE agent_proposal ALTER COLUMN source SET NOT NULL;
ALTER TABLE agent_proposal ADD CHECK (source IN ('TWILIO','KAPSO','APP_TEST'));

CREATE FUNCTION project_agent_proposal_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 SELECT m.source INTO NEW.source
 FROM agent_run r JOIN agent_inbox_message m ON m.id=r.message_id
 WHERE r.id=NEW.run_id AND r.conversation_id=NEW.conversation_id;
 IF NEW.source IS NULL THEN
  RAISE EXCEPTION 'Proposal requires a matching input source' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER agent_proposal_source_projection
BEFORE INSERT OR UPDATE OF run_id,conversation_id,source ON agent_proposal
FOR EACH ROW EXECUTE FUNCTION project_agent_proposal_source();

DROP INDEX agent_one_pending_proposal;
CREATE UNIQUE INDEX agent_one_pending_proposal ON agent_proposal(conversation_id,source)
WHERE state='PENDING';
