CREATE TABLE institutions (
    id uuid PRIMARY KEY,
    name varchar(255) NOT NULL UNIQUE
);

INSERT INTO institutions (id, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'CertiChain');

ALTER TABLE users ADD COLUMN institution_id uuid;
UPDATE users SET institution_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE users ALTER COLUMN institution_id SET NOT NULL;
ALTER TABLE users
    ADD CONSTRAINT users_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions (id);

ALTER TABLE students ADD COLUMN institution_id uuid;
UPDATE students SET institution_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE students ALTER COLUMN institution_id SET NOT NULL;
ALTER TABLE students
    ADD CONSTRAINT students_institution_fk FOREIGN KEY (institution_id) REFERENCES institutions (id);

ALTER TABLE students DROP CONSTRAINT IF EXISTS students_student_number_key;
CREATE UNIQUE INDEX students_institution_number_key ON students (institution_id, student_number);

CREATE TABLE chain_settings (
    id integer PRIMARY KEY,
    rpc_url varchar(512) NOT NULL,
    chain_id bigint NOT NULL,
    private_key varchar(200) NOT NULL DEFAULT ''
);
