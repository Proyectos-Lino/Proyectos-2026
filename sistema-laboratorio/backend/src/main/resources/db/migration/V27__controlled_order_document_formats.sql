ALTER TABLE organization_profile
    ADD COLUMN service_order_format_code VARCHAR(40),
    ADD COLUMN service_order_format_version VARCHAR(20),
    ADD COLUMN service_order_format_approved_on DATE,
    ADD COLUMN work_order_format_code VARCHAR(40),
    ADD COLUMN work_order_format_version VARCHAR(20),
    ADD COLUMN work_order_format_approved_on DATE;
