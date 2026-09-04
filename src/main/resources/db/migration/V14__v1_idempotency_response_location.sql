ALTER TABLE idempotency_record
    ADD COLUMN response_location VARCHAR(512) NULL AFTER response_etag;
