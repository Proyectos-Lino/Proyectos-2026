ALTER TABLE result_template_category ADD COLUMN legend VARCHAR(500);

CREATE TABLE result_template_carrier_shape (
    result_template_id UUID NOT NULL REFERENCES result_template(id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL,
    name VARCHAR(120) NOT NULL,
    PRIMARY KEY (result_template_id, display_order),
    CHECK (length(trim(name)) > 0)
);
CREATE UNIQUE INDEX uq_template_carrier_shape_name
    ON result_template_carrier_shape(result_template_id, lower(name));

ALTER TABLE result_entry ADD COLUMN matrix_configuration_snapshot TEXT;

-- Preserve the configuration used by existing matrices before their catalog is edited.
UPDATE result_entry entry
SET matrix_configuration_snapshot = jsonb_build_object(
    'defaultRows', template.default_rows, 'maximumRows', template.maximum_rows,
    'defaultColumns', template.default_columns, 'maximumColumns', template.maximum_columns,
    'categories', COALESCE((SELECT jsonb_agg(jsonb_build_object(
        'id', category.id, 'code', category.code, 'label', category.label,
        'displayOrder', category.display_order, 'legend', category.legend)
        ORDER BY category.display_order)
        FROM result_template_category category WHERE category.result_template_id = template.id), '[]'::jsonb),
    'carrierShapes', '[]'::jsonb
)::text
FROM result_template template
WHERE entry.result_template_id = template.id AND entry.result_mode = 'MATRIX';
