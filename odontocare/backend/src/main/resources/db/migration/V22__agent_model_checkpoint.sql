-- Resume completed model/tool exchanges without repeating them after a transient limit.
ALTER TABLE agent_run ADD COLUMN model_checkpoint jsonb;
