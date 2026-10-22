-- Include child-contact edits in the patient aggregate's optimistic version.
ALTER TABLE patient ADD COLUMN contact_revision BIGINT NOT NULL DEFAULT 0 CHECK (contact_revision >= 0);
