ALTER TABLE certificates ADD COLUMN IF NOT EXISTS expiry_date date;
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS signature text NOT NULL DEFAULT '';
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS signing_key_id varchar(64) NOT NULL DEFAULT '';
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS signer_public_key text NOT NULL DEFAULT '';

ALTER TABLE share_links ADD COLUMN IF NOT EXISTS allow_preview boolean NOT NULL DEFAULT true;

CREATE TABLE document_extractions (
    id uuid PRIMARY KEY,
    document_id uuid NOT NULL REFERENCES wallet_documents (id),
    status varchar(32) NOT NULL,
    method varchar(32) NOT NULL,
    raw_text text NOT NULL DEFAULT '',
    note varchar(500) NOT NULL DEFAULT '',
    suggested_category varchar(64) NOT NULL DEFAULT '',
    category_confidence varchar(16) NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL
);

CREATE INDEX document_extractions_document_idx ON document_extractions (document_id, created_at DESC);

CREATE TABLE extracted_fields (
    id uuid PRIMARY KEY,
    extraction_id uuid NOT NULL REFERENCES document_extractions (id),
    field_name varchar(64) NOT NULL,
    candidate_value varchar(500) NOT NULL DEFAULT '',
    corrected_value varchar(500) NOT NULL DEFAULT '',
    confidence varchar(16) NOT NULL,
    source_page integer NOT NULL DEFAULT 1,
    method varchar(32) NOT NULL,
    review_status varchar(32) NOT NULL
);

CREATE TABLE share_access_events (
    id uuid PRIMARY KEY,
    share_id uuid NOT NULL REFERENCES share_links (id),
    event_type varchar(32) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE INDEX share_access_events_share_idx ON share_access_events (share_id, created_at DESC);

CREATE TABLE bulk_jobs (
    id uuid PRIMARY KEY,
    institution_id uuid NOT NULL REFERENCES institutions (id),
    actor_email varchar(255) NOT NULL,
    status varchar(32) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE bulk_rows (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES bulk_jobs (id),
    row_number integer NOT NULL,
    student_number varchar(64) NOT NULL DEFAULT '',
    certificate_id varchar(64) NOT NULL DEFAULT '',
    status varchar(32) NOT NULL,
    detail varchar(500) NOT NULL DEFAULT '',
    payload text NOT NULL DEFAULT ''
);

CREATE TABLE credential_amendments (
    id uuid PRIMARY KEY,
    certificate_id varchar(64) NOT NULL,
    actor_email varchar(255) NOT NULL DEFAULT '',
    reason varchar(500) NOT NULL,
    previous_hash varchar(64) NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL
);

CREATE INDEX credential_amendments_certificate_idx ON credential_amendments (certificate_id, created_at DESC);

CREATE TABLE anomaly_flags (
    id uuid PRIMARY KEY,
    institution_id uuid,
    rule_name varchar(64) NOT NULL,
    severity varchar(32) NOT NULL,
    target_id varchar(128) NOT NULL DEFAULT '',
    detail varchar(500) NOT NULL,
    review_status varchar(32) NOT NULL,
    review_note varchar(500) NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL
);

CREATE INDEX anomaly_flags_institution_idx ON anomaly_flags (institution_id, created_at DESC);
