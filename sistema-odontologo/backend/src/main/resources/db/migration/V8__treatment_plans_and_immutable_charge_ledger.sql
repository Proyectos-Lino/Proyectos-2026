CREATE TABLE treatment_plan (
 id UUID PRIMARY KEY, patient_id UUID NOT NULL REFERENCES patient(id), dentist_id UUID NOT NULL REFERENCES dentist(id),
 dentist_name VARCHAR(120) NOT NULL, patient_name VARCHAR(160) NOT NULL, code VARCHAR(30) NOT NULL UNIQUE,
 title VARCHAR(160) NOT NULL, conditions TEXT NOT NULL CHECK(length(conditions)<=4000), currency VARCHAR(3) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN('DRAFT','PROPOSED','ACCEPTED','IN_PROGRESS','FINISHED','CANCELLED')),
 accepted_total NUMERIC(14,2),accepted_by VARCHAR(160),accepted_at TIMESTAMPTZ,version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_treatment_patient ON treatment_plan(patient_id,status,created_at DESC,id);
CREATE TABLE treatment_item (
 id UUID PRIMARY KEY,plan_id UUID NOT NULL REFERENCES treatment_plan(id),service_id UUID REFERENCES dental_service(id),
 service_name VARCHAR(120) NOT NULL,description VARCHAR(300) NOT NULL,tooth INTEGER CHECK((tooth/10 BETWEEN 1 AND 4 AND tooth%10 BETWEEN 1 AND 8) OR(tooth/10 BETWEEN 5 AND 8 AND tooth%10 BETWEEN 1 AND 5)),
 quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 100),sessions INTEGER NOT NULL CHECK(sessions BETWEEN 1 AND 100),
 unit_price NUMERIC(10,2) NOT NULL CHECK(unit_price>=0),position INTEGER NOT NULL CHECK(position>0),
 version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(plan_id,position),UNIQUE(plan_id,id)
);
CREATE INDEX idx_treatment_item_plan ON treatment_item(plan_id,id);
CREATE TABLE treatment_operation (
 id UUID PRIMARY KEY,plan_id UUID NOT NULL REFERENCES treatment_plan(id),request_key UUID NOT NULL UNIQUE,
 fingerprint VARCHAR(64) NOT NULL,action VARCHAR(24) NOT NULL,reason VARCHAR(500) NOT NULL,actor_name VARCHAR(120) NOT NULL,summary VARCHAR(1000) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_treatment_operation_plan ON treatment_operation(plan_id,created_at DESC,id);
CREATE TABLE treatment_session (
 id UUID PRIMARY KEY,plan_id UUID NOT NULL REFERENCES treatment_plan(id),item_id UUID NOT NULL,
 encounter_id UUID NOT NULL REFERENCES clinical_encounter(id),procedure_index INTEGER NOT NULL CHECK(procedure_index>=0),
 sessions INTEGER NOT NULL CHECK(sessions BETWEEN 1 AND 100),actor_name VARCHAR(120) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(encounter_id,procedure_index),FOREIGN KEY(plan_id,item_id) REFERENCES treatment_item(plan_id,id)
);
CREATE INDEX idx_treatment_session_item ON treatment_session(item_id,id);
CREATE TABLE charge_entry (
 id UUID PRIMARY KEY,patient_id UUID NOT NULL REFERENCES patient(id),plan_id UUID REFERENCES treatment_plan(id),item_id UUID,
 encounter_id UUID REFERENCES clinical_encounter(id),original_id UUID REFERENCES charge_entry(id),source_key VARCHAR(100) NOT NULL UNIQUE,
 fingerprint VARCHAR(64) NOT NULL,kind VARCHAR(24) NOT NULL CHECK(kind IN('PLAN','SERVICE','ADJUSTMENT','CANCELLATION')),
 description VARCHAR(300) NOT NULL,currency VARCHAR(3) NOT NULL,amount NUMERIC(14,2) NOT NULL,
 unit_price NUMERIC(10,2),quantity INTEGER,reason VARCHAR(500) NOT NULL,actor_name VARCHAR(120) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(plan_id,item_id) REFERENCES treatment_item(plan_id,id),
 CHECK((kind IN('PLAN','SERVICE') AND original_id IS NULL AND amount>=0 AND unit_price>=0 AND quantity>0 AND amount=unit_price*quantity)
 OR (kind IN('ADJUSTMENT','CANCELLATION') AND original_id IS NOT NULL AND amount<>0))
);
CREATE INDEX idx_charge_patient ON charge_entry(patient_id,currency,created_at DESC,id);
CREATE INDEX idx_charge_origin ON charge_entry(original_id);
CREATE INDEX idx_charge_item ON charge_entry(item_id);
CREATE TRIGGER charge_entry_immutable BEFORE UPDATE OR DELETE ON charge_entry FOR EACH ROW EXECUTE FUNCTION protect_clinical_original();
CREATE TRIGGER treatment_operation_immutable BEFORE UPDATE OR DELETE ON treatment_operation FOR EACH ROW EXECUTE FUNCTION protect_clinical_original();
CREATE TRIGGER treatment_session_immutable BEFORE UPDATE OR DELETE ON treatment_session FOR EACH ROW EXECUTE FUNCTION protect_clinical_original();
CREATE FUNCTION protect_accepted_treatment_item() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF (SELECT accepted_at IS NOT NULL FROM treatment_plan WHERE id=OLD.plan_id) THEN RAISE EXCEPTION 'Accepted treatment item is immutable'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
CREATE TRIGGER treatment_item_original BEFORE UPDATE OR DELETE ON treatment_item FOR EACH ROW EXECUTE FUNCTION protect_accepted_treatment_item();
CREATE FUNCTION protect_accepted_treatment() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.accepted_at IS NOT NULL AND (NEW.patient_id IS DISTINCT FROM OLD.patient_id OR NEW.dentist_id IS DISTINCT FROM OLD.dentist_id OR NEW.title IS DISTINCT FROM OLD.title OR NEW.conditions IS DISTINCT FROM OLD.conditions OR NEW.currency IS DISTINCT FROM OLD.currency OR NEW.code IS DISTINCT FROM OLD.code OR NEW.dentist_name IS DISTINCT FROM OLD.dentist_name OR NEW.patient_name IS DISTINCT FROM OLD.patient_name OR NEW.accepted_at IS DISTINCT FROM OLD.accepted_at OR NEW.accepted_by IS DISTINCT FROM OLD.accepted_by OR NEW.accepted_total IS DISTINCT FROM OLD.accepted_total) THEN RAISE EXCEPTION 'Accepted agreement is immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER treatment_plan_original BEFORE UPDATE ON treatment_plan FOR EACH ROW EXECUTE FUNCTION protect_accepted_treatment();
ALTER TABLE patient_document ADD COLUMN plan_id UUID REFERENCES treatment_plan(id);
CREATE INDEX idx_document_plan ON patient_document(plan_id);
INSERT INTO role_permission(role_code,permission)
 SELECT role_code,permission FROM unnest(ARRAY['ADMIN','DENTIST','RECEPTION','CASHIER']) role_code CROSS JOIN unnest(ARRAY['PLANS_READ']) permission ON CONFLICT DO NOTHING;
INSERT INTO role_permission(role_code,permission)
 SELECT role_code,permission FROM unnest(ARRAY['ADMIN','DENTIST','RECEPTION']) role_code CROSS JOIN unnest(ARRAY['PLANS_WRITE']) permission ON CONFLICT DO NOTHING;
INSERT INTO role_permission(role_code,permission)
 SELECT role_code,permission FROM unnest(ARRAY['ADMIN','DENTIST','CASHIER']) role_code CROSS JOIN unnest(ARRAY['FINANCES_READ']) permission ON CONFLICT DO NOTHING;
INSERT INTO role_permission(role_code,permission) VALUES('ADMIN','FINANCES_ADJUST') ON CONFLICT DO NOTHING;
