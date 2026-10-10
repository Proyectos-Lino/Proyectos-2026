ALTER TABLE service_order
    ADD COLUMN confirmed_at TIMESTAMPTZ;

UPDATE service_order current_order
SET confirmed_at = COALESCE(
    (
        SELECT MIN(history.occurred_at)
        FROM service_order_history history
        WHERE history.service_order_id = current_order.id
          AND history.action = 'PROCESSING_STARTED'
    ),
    current_order.created_at
)
WHERE current_order.status IN ('IN_PROGRESS', 'FINALIZED')
   OR EXISTS (
       SELECT 1
       FROM service_order_history history
       WHERE history.service_order_id = current_order.id
         AND history.action = 'PROCESSING_STARTED'
   );

UPDATE service_order_history
SET action = 'CONFIRMED',
    change_note = 'Orden confirmada y enviada al laboratorio para su procesamiento técnico.'
WHERE action = 'PROCESSING_STARTED';

CREATE INDEX idx_work_order_service_order_status
    ON work_order(service_order_id, status);

CREATE INDEX idx_work_order_sample_work_order
    ON work_order_sample(work_order_id);

CREATE INDEX idx_work_order_test_source_status
    ON work_order_test(source_sample_test_id, status);
