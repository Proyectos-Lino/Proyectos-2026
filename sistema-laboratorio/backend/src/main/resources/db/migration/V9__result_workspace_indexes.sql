CREATE INDEX idx_work_order_test_result_queue
    ON work_order_test(assigned_analyst_id, status, updated_at DESC);

CREATE INDEX idx_result_entry_validation_queue
    ON result_entry(status, recorded_at ASC);

CREATE INDEX idx_result_change_log_value_changed
    ON result_change_log(result_value_id, changed_at DESC);
