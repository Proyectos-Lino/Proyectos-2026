INSERT INTO agent_supervision(conversation_id) SELECT id FROM agent_conversation_source ON CONFLICT DO NOTHING;
INSERT INTO agent_request_context(conversation_id,source,state,summary,patient_id,patient_name,appointment_id,updated_at)
 SELECT DISTINCT ON(p.conversation_id,p.source) p.conversation_id,p.source,
 CASE p.state WHEN 'CONFIRMED' THEN 'COMPLETED' WHEN 'PENDING' THEN 'CONFIRMATION_PENDING' WHEN 'EXPIRED' THEN 'EXPIRED' ELSE 'INFORMATION_PENDING' END,
 p.summary,p.patient_id,p.patient_name,p.appointment_id,p.created_at
 FROM agent_proposal p JOIN agent_run r ON r.id=p.run_id ORDER BY p.conversation_id,p.source,r.sequence_no DESC
 ON CONFLICT DO NOTHING;
-- Historical phone matches are administrative associations, never verified identities.
CREATE INDEX agent_supervision_queue ON agent_supervision(mode,updated_at,conversation_id);
CREATE INDEX agent_context_state ON agent_request_context(source,state,conversation_id);
CREATE INDEX agent_own_appointments ON appointment(patient_id,starts_at,id) WHERE status IN ('RESERVED','CONFIRMED');
