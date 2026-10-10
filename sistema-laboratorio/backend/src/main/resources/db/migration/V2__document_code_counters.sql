CREATE TABLE document_code_counter (
    document_type VARCHAR(30) NOT NULL,
    calendar_year INTEGER NOT NULL,
    last_value INTEGER NOT NULL,
    PRIMARY KEY (document_type, calendar_year)
);

