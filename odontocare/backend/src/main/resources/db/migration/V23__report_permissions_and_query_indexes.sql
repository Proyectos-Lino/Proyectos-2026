INSERT INTO role_permission(role_code,permission)
SELECT role_code,permission FROM unnest(ARRAY['ADMIN','DENTIST','RECEPTION','CASHIER']) role_code
CROSS JOIN unnest(ARRAY['REPORTS_READ','REPORTS_EXPORT']) permission ON CONFLICT DO NOTHING;
UPDATE user_account SET auth_version=auth_version+1 WHERE id IN (SELECT user_id FROM user_role WHERE role_code IN ('ADMIN','DENTIST','RECEPTION','CASHIER'));
CREATE INDEX idx_report_money_date ON money_movement(occurred_on,kind,currency,id);
CREATE INDEX idx_report_charge_date ON charge_entry(created_at,id);
CREATE INDEX idx_report_encounter_date ON clinical_encounter(attended_on,dentist_id,id) WHERE status='FINAL';
CREATE INDEX idx_report_plan_date ON treatment_plan(created_at,id);
CREATE INDEX idx_report_plan_acceptance ON treatment_plan(accepted_at,id) WHERE accepted_at IS NOT NULL;
CREATE INDEX idx_report_session_date ON treatment_session(created_at,plan_id);
CREATE INDEX idx_report_application_date ON money_application(created_at,charge_id);
CREATE INDEX idx_report_schedule_cut ON installment_schedule(charge_id,created_at DESC,id);
