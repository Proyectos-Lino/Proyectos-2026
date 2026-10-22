ALTER TABLE clinical_state ADD COLUMN sequence INTEGER NOT NULL DEFAULT 1 CHECK(sequence>0);
ALTER TABLE clinical_state ADD COLUMN dentist_id UUID REFERENCES dentist(id);
ALTER TABLE clinical_state ADD COLUMN dentist_name VARCHAR(120);
ALTER TABLE clinical_state DISABLE TRIGGER clinical_state_immutable;
WITH RECURSIVE state_chain AS (
 SELECT id,1 AS depth FROM clinical_state WHERE previous_id IS NULL
 UNION ALL SELECT child.id,parent.depth+1 FROM clinical_state child JOIN state_chain parent ON child.previous_id=parent.id
) UPDATE clinical_state SET sequence=state_chain.depth FROM state_chain WHERE clinical_state.id=state_chain.id;
ALTER TABLE clinical_state ENABLE TRIGGER clinical_state_immutable;
CREATE UNIQUE INDEX uq_clinical_state_sequence ON clinical_state(patient_id,kind,sequence);
CREATE UNIQUE INDEX uq_clinical_state_successor ON clinical_state(previous_id) WHERE previous_id IS NOT NULL;
CREATE INDEX idx_clinical_state_latest ON clinical_state(patient_id,kind,sequence DESC);
