CREATE TABLE technical_sample (
    id UUID PRIMARY KEY,
    service_order_sample_id UUID NOT NULL UNIQUE REFERENCES service_order_sample(id),
    declared_sample_type_id UUID NOT NULL REFERENCES sample_type(id),
    technical_sample_type_id UUID NOT NULL REFERENCES sample_type(id),
    laboratory_code VARCHAR(40) NOT NULL UNIQUE,
    laboratory_sequence INTEGER NOT NULL,
    classified_by VARCHAR(180) NOT NULL,
    classified_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO technical_sample (
    id,
    service_order_sample_id,
    declared_sample_type_id,
    technical_sample_type_id,
    laboratory_code,
    laboratory_sequence,
    classified_by,
    classified_at,
    created_at,
    updated_at,
    version
)
SELECT
    existing.id,
    existing.service_order_sample_id,
    existing.declared_sample_type_id,
    existing.technical_sample_type_id,
    existing.laboratory_code,
    existing.laboratory_sequence,
    'migration@labcore.local',
    existing.created_at,
    existing.created_at,
    existing.updated_at,
    0
FROM (
    SELECT DISTINCT ON (work_sample.service_order_sample_id)
        work_sample.id,
        work_sample.service_order_sample_id,
        source_sample.sample_type_id AS declared_sample_type_id,
        work_sample.sample_type_id AS technical_sample_type_id,
        work_sample.laboratory_code,
        work_sample.laboratory_sequence,
        work_sample.created_at,
        work_sample.updated_at
    FROM work_order_sample work_sample
    JOIN service_order_sample source_sample ON source_sample.id = work_sample.service_order_sample_id
    ORDER BY work_sample.service_order_sample_id, work_sample.created_at, work_sample.id
) existing;

ALTER TABLE work_order_sample
    ADD COLUMN technical_sample_id UUID REFERENCES technical_sample(id),
    ADD COLUMN declared_sample_type_id UUID REFERENCES sample_type(id);

UPDATE work_order_sample work_sample
SET technical_sample_id = technical.id,
    declared_sample_type_id = source_sample.sample_type_id
FROM technical_sample technical,
     service_order_sample source_sample
WHERE technical.service_order_sample_id = work_sample.service_order_sample_id
  AND source_sample.id = work_sample.service_order_sample_id;

ALTER TABLE work_order_sample
    ALTER COLUMN technical_sample_id SET NOT NULL,
    ALTER COLUMN declared_sample_type_id SET NOT NULL;

-- El código identifica a la muestra técnica y puede aparecer en varias OT cuando sus ensayos
-- se distribuyen por áreas o emisiones distintas. La unicidad vive en technical_sample.
ALTER TABLE work_order_sample
    DROP CONSTRAINT IF EXISTS work_order_sample_laboratory_code_key;

UPDATE work_order_sample work_sample
SET sample_type_id = technical.technical_sample_type_id,
    laboratory_code = technical.laboratory_code,
    laboratory_sequence = technical.laboratory_sequence
FROM technical_sample technical
WHERE technical.id = work_sample.technical_sample_id;

CREATE INDEX idx_technical_sample_type_created
    ON technical_sample(technical_sample_type_id, created_at DESC);

CREATE INDEX idx_work_order_sample_technical
    ON work_order_sample(technical_sample_id);

CREATE INDEX idx_work_order_sample_laboratory_code
    ON work_order_sample(laboratory_code);
