ALTER TABLE users ALTER COLUMN institution_id DROP NOT NULL;

ALTER TABLE students ADD COLUMN IF NOT EXISTS email varchar(255);
CREATE INDEX IF NOT EXISTS students_email_idx ON students (lower(email));

CREATE TABLE wallet_documents (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES users (id),
    title varchar(255) NOT NULL,
    category varchar(64) NOT NULL,
    issuer_name varchar(255) NOT NULL DEFAULT '',
    storage_key varchar(512) NOT NULL,
    content_type varchar(128) NOT NULL,
    file_size bigint NOT NULL,
    sha256 varchar(64) NOT NULL,
    status varchar(64) NOT NULL,
    description text NOT NULL DEFAULT '',
    issue_date date,
    expiry_date date,
    credential_number varchar(128) NOT NULL DEFAULT '',
    tags varchar(512) NOT NULL DEFAULT '',
    extracted_text text NOT NULL DEFAULT '',
    archived boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL
);

CREATE INDEX wallet_documents_owner_idx ON wallet_documents (owner_id, created_at DESC);

CREATE TABLE share_links (
    id uuid PRIMARY KEY,
    token_hash varchar(64) NOT NULL UNIQUE,
    owner_id uuid NOT NULL REFERENCES users (id),
    recipient_label varchar(255) NOT NULL DEFAULT '',
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    max_access integer,
    access_count integer NOT NULL DEFAULT 0,
    allow_download boolean NOT NULL DEFAULT false,
    note varchar(500) NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL
);

CREATE TABLE share_items (
    id uuid PRIMARY KEY,
    share_id uuid NOT NULL REFERENCES share_links (id),
    document_id uuid REFERENCES wallet_documents (id),
    certificate_id varchar(64),
    CONSTRAINT share_items_one_target CHECK (
        (document_id IS NOT NULL AND certificate_id IS NULL)
        OR (document_id IS NULL AND certificate_id IS NOT NULL)
    )
);

CREATE INDEX share_items_share_idx ON share_items (share_id);

CREATE TABLE notifications (
    id uuid PRIMARY KEY,
    recipient_id uuid NOT NULL REFERENCES users (id),
    event_type varchar(64) NOT NULL,
    title varchar(255) NOT NULL,
    message varchar(500) NOT NULL,
    href varchar(255) NOT NULL DEFAULT '',
    read_at timestamptz,
    created_at timestamptz NOT NULL
);

CREATE INDEX notifications_recipient_idx ON notifications (recipient_id, created_at DESC);

CREATE TABLE audit_events (
    id uuid PRIMARY KEY,
    actor_email varchar(255) NOT NULL DEFAULT '',
    event_type varchar(64) NOT NULL,
    target_type varchar(64) NOT NULL,
    target_id varchar(128) NOT NULL DEFAULT '',
    detail varchar(500) NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL
);

CREATE INDEX audit_events_created_idx ON audit_events (created_at DESC);

CREATE TABLE verifier_checks (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    certificate_id varchar(64) NOT NULL,
    result varchar(32) NOT NULL,
    checked_at timestamptz NOT NULL
);

CREATE INDEX verifier_checks_user_idx ON verifier_checks (user_id, checked_at DESC);
