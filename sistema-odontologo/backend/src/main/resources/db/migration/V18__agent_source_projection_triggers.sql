-- Every writer, including the preserved Twilio version, keeps agent references consistent.
CREATE FUNCTION agent_register_conversation_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='whatsapp_conversation' THEN
   INSERT INTO agent_conversation_source(id,provider,whatsapp_id) VALUES(NEW.id,'TWILIO',NEW.id) ON CONFLICT(id) DO NOTHING;
 ELSE
   INSERT INTO agent_conversation_source(id,provider,kapso_id) VALUES(NEW.id,'KAPSO',NEW.id) ON CONFLICT(id) DO NOTHING;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER whatsapp_agent_conversation AFTER INSERT ON whatsapp_conversation FOR EACH ROW EXECUTE FUNCTION agent_register_conversation_source();
CREATE TRIGGER kapso_agent_conversation AFTER INSERT ON kapso_conversation FOR EACH ROW EXECUTE FUNCTION agent_register_conversation_source();
CREATE FUNCTION agent_register_message_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='whatsapp_message' THEN
   INSERT INTO agent_message_source(id,conversation_id,provider,whatsapp_id) VALUES(NEW.id,NEW.conversation_id,'TWILIO',NEW.id) ON CONFLICT(id) DO NOTHING;
 ELSE
   INSERT INTO agent_message_source(id,conversation_id,provider,kapso_id) VALUES(NEW.id,NEW.conversation_id,'KAPSO',NEW.id) ON CONFLICT(id) DO NOTHING;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER whatsapp_agent_message AFTER INSERT ON whatsapp_message FOR EACH ROW EXECUTE FUNCTION agent_register_message_source();
CREATE TRIGGER kapso_agent_message AFTER INSERT ON kapso_message FOR EACH ROW EXECUTE FUNCTION agent_register_message_source();
