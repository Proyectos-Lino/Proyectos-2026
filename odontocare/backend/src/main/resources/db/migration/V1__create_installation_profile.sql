CREATE TABLE installation_profile (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    display_name VARCHAR(120) NOT NULL CHECK (length(trim(display_name)) > 0),
    time_zone VARCHAR(60) NOT NULL,
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO installation_profile (id, display_name, time_zone, currency)
VALUES (1, 'Mi consultorio', 'America/Lima', 'PEN');
