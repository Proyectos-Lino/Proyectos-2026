ALTER TABLE service_order_sample ADD COLUMN producer_name VARCHAR(220);

UPDATE service_order_sample sample
SET producer_name = participant.legal_name
FROM client participant
WHERE participant.id = sample.producer_id;
