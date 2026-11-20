CREATE TABLE technical_sample_code_counter (
    prefix VARCHAR(20) PRIMARY KEY,
    last_value INTEGER NOT NULL CHECK (last_value > 0)
);

-- Continue from the highest allocated sequence for each abbreviation, across all years.
INSERT INTO technical_sample_code_counter (prefix, last_value)
SELECT prefix, MAX(last_value)
FROM (
    SELECT UPPER(split_part(laboratory_code, '-', 1)) AS prefix,
           laboratory_sequence AS last_value
    FROM technical_sample
    UNION ALL
    SELECT UPPER(sample_type.laboratory_code_prefix), counter.last_value
    FROM sample_code_counter counter
    JOIN sample_type ON sample_type.id = counter.sample_type_id
) historical
WHERE last_value > 0 AND prefix ~ '^[A-Z0-9]{1,20}$'
GROUP BY prefix;
