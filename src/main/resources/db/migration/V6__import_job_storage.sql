ALTER TABLE import_job
    ADD COLUMN IF NOT EXISTS storage_bucket VARCHAR(255),
    ADD COLUMN IF NOT EXISTS storage_object_key VARCHAR(512),
    ADD COLUMN IF NOT EXISTS storage_content_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS storage_size_bytes BIGINT;
