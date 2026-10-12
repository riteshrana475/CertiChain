CREATE TABLE signing_keys (
    id integer PRIMARY KEY,
    private_key text NOT NULL,
    public_key text NOT NULL,
    key_id varchar(64) NOT NULL
);

ALTER TABLE wallet_documents ADD COLUMN scan_status varchar(32) NOT NULL DEFAULT 'UNSCANNED';
