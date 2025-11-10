ALTER TABLE study_group
    ADD COLUMN IF NOT EXISTS course INTEGER,
    ADD COLUMN IF NOT EXISTS sequence_number INTEGER;

UPDATE study_group
SET course = COALESCE(course, 1),
    sequence_number = COALESCE(sequence_number, id);

ALTER TABLE study_group
    ALTER COLUMN course SET NOT NULL,
    ALTER COLUMN sequence_number SET NOT NULL;

UPDATE study_group
SET students_count = COALESCE(students_count, 1),
    form_of_education = COALESCE(form_of_education, 'FULL_TIME_EDUCATION');

ALTER TABLE study_group
    ALTER COLUMN students_count SET NOT NULL,
    ALTER COLUMN form_of_education SET NOT NULL;

CREATE TABLE IF NOT EXISTS import_job
(
    id            UUID PRIMARY KEY,
    entity_type   VARCHAR(64)                        NOT NULL,
    status        VARCHAR(32)                        NOT NULL,
    username      VARCHAR(255)                       NOT NULL,
    filename      VARCHAR(255)                       NOT NULL,
    total_records INTEGER,
    success_count INTEGER,
    error_message TEXT,
    created_at    TIMESTAMP WITHOUT TIME ZONE        NOT NULL DEFAULT now(),
    finished_at   TIMESTAMP WITHOUT TIME ZONE
);
