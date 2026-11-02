ALTER TABLE service_order_sample
    ADD COLUMN product_id UUID REFERENCES product(id),
    ADD COLUMN variety_id UUID REFERENCES product_variety(id);

-- Link only unambiguous historical names, retaining the original document text.
UPDATE service_order_sample s SET product_id = p.id
FROM product p
WHERE lower(trim(s.product_name)) = lower(trim(p.name))
  AND (SELECT count(*) FROM product other WHERE lower(trim(other.name)) = lower(trim(p.name))) = 1;

UPDATE service_order_sample s SET variety_id = v.id
FROM product_variety v
WHERE s.product_id = v.product_id
  AND lower(trim(s.variety_name)) = lower(trim(v.name))
  AND (SELECT count(*) FROM product_variety other
       WHERE other.product_id = v.product_id AND lower(trim(other.name)) = lower(trim(v.name))) = 1;

CREATE INDEX idx_sample_product ON service_order_sample(product_id);
