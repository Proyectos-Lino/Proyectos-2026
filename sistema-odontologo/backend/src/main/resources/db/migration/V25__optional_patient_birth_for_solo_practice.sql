-- The solo-practice variant treats completion as an informational status.
-- Keep identity, contact, document and financial integrity constraints.
ALTER TABLE patient DROP CONSTRAINT patient_check;
