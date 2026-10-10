CREATE INDEX idx_service_order_dashboard_period_area
    ON service_order(created_at, laboratory_area_id);

CREATE INDEX idx_service_order_sample_created
    ON service_order_sample(created_at, service_order_id);

CREATE INDEX idx_result_entry_recorded_user
    ON result_entry(recorded_at, recorded_by);

CREATE INDEX idx_result_validation_validated
    ON result_validation(validated_at, result_entry_id);

CREATE INDEX idx_laboratory_report_issued
    ON laboratory_report(issued_at, service_order_id)
    WHERE status = 'ISSUED';
