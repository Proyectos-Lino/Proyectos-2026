ALTER TABLE financial_document ADD COLUMN cash_session_id UUID REFERENCES cash_session(id);
CREATE UNIQUE INDEX idx_cash_report ON financial_document(cash_session_id) WHERE generated AND cash_session_id IS NOT NULL;
CREATE UNIQUE INDEX idx_generated_receipt ON financial_document(movement_id) WHERE generated AND movement_id IS NOT NULL;
CREATE UNIQUE INDEX idx_expense_category_name ON expense_category(lower(name));
CREATE FUNCTION protect_installment_schedule() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF TG_OP='DELETE' OR NEW.patient_id IS DISTINCT FROM OLD.patient_id OR NEW.charge_id IS DISTINCT FROM OLD.charge_id OR NEW.total IS DISTINCT FROM OLD.total OR NEW.reason IS DISTINCT FROM OLD.reason OR NEW.actor_name IS DISTINCT FROM OLD.actor_name OR (OLD.active=false AND NEW.active=true) THEN RAISE EXCEPTION 'Installment schedule history is immutable'; END IF; RETURN NEW; END $$;
CREATE TRIGGER installment_schedule_history BEFORE UPDATE OR DELETE ON installment_schedule FOR EACH ROW EXECUTE FUNCTION protect_installment_schedule();
