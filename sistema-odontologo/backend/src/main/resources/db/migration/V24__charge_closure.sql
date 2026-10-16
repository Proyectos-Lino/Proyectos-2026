-- Closure is administrative: original charges and money remain immutable.
CREATE TABLE charge_state (
 charge_id UUID PRIMARY KEY REFERENCES charge_entry(id),
 closed BOOLEAN NOT NULL DEFAULT FALSE,
 changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE OR REPLACE VIEW financial_charge_balance AS
 SELECT c.id,c.patient_id,c.description,c.currency,
 c.amount + coalesce((SELECT sum(a.amount) FROM charge_entry a WHERE a.original_id=c.id),0) AS debt,
 coalesce((SELECT sum(m.amount) FROM money_application m WHERE m.charge_id=c.id),0) AS applied,
 c.amount + coalesce((SELECT sum(a.amount) FROM charge_entry a WHERE a.original_id=c.id),0)
 - coalesce((SELECT sum(m.amount) FROM money_application m WHERE m.charge_id=c.id),0) AS pending,
 coalesce(s.closed,FALSE) AS closed,c.encounter_id
 FROM charge_entry c LEFT JOIN charge_state s ON s.charge_id=c.id WHERE c.original_id IS NULL;
CREATE FUNCTION guard_closed_charge() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE target UUID; patient_ref UUID;
BEGIN
 IF TG_TABLE_NAME='charge_state' THEN target:=NEW.charge_id;
 ELSIF TG_TABLE_NAME='money_application' THEN target:=NEW.charge_id;
 ELSIF TG_TABLE_NAME='installment_schedule' THEN target:=NEW.charge_id;
 ELSE target:=NEW.original_id; END IF;
 IF target IS NULL THEN RETURN NEW; END IF;
 SELECT patient_id INTO patient_ref FROM charge_entry WHERE id=target AND original_id IS NULL;
 IF patient_ref IS NULL THEN RAISE EXCEPTION 'Only an original charge can be closed or modified'; END IF;
 PERFORM id FROM patient WHERE id=patient_ref FOR UPDATE;
 IF TG_TABLE_NAME='charge_state' THEN
   IF NEW.closed AND EXISTS(SELECT 1 FROM financial_charge_balance WHERE id=target AND pending<>0)
   THEN RAISE EXCEPTION 'A charge with pending balance cannot be closed'; END IF;
 ELSIF EXISTS(SELECT 1 FROM charge_state WHERE charge_id=target AND closed) THEN
   RAISE EXCEPTION 'Reopen the charge before changing its balance or payments';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER charge_state_guard BEFORE INSERT OR UPDATE ON charge_state FOR EACH ROW EXECUTE FUNCTION guard_closed_charge();
CREATE TRIGGER charge_application_guard BEFORE INSERT ON money_application FOR EACH ROW EXECUTE FUNCTION guard_closed_charge();
CREATE TRIGGER charge_adjustment_guard BEFORE INSERT ON charge_entry FOR EACH ROW EXECUTE FUNCTION guard_closed_charge();
CREATE TRIGGER charge_installment_guard BEFORE INSERT ON installment_schedule FOR EACH ROW EXECUTE FUNCTION guard_closed_charge();
