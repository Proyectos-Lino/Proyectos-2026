-- Los CHECK de PostgreSQL aceptan UNKNOWN: exigir explícitamente la pareja completa.
ALTER TABLE schedule_exception ADD CONSTRAINT schedule_exception_complete_times
CHECK ((start_minute IS NULL AND end_minute IS NULL)
    OR (start_minute IS NOT NULL AND end_minute IS NOT NULL));
