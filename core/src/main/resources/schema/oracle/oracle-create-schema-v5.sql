CREATE TABLE EVENT_JOURNAL (
    ORDERING NUMBER(19) NOT NULL,
    DELETED CHAR(1) DEFAULT 0 NOT NULL check (DELETED in (0, 1)),
    PERSISTENCE_ID VARCHAR2(255) NOT NULL,
    SEQUENCE_NUMBER NUMBER(19) NOT NULL,
    WRITER VARCHAR2(254) NOT NULL,
    WRITE_TIMESTAMP NUMBER(19) NOT NULL,
    EVENT_MANIFEST VARCHAR2(254),
    EVENT_PAYLOAD BLOB NOT NULL,
    EVENT_SER_ID NUMBER(10) NOT NULL,
    EVENT_SER_MANIFEST VARCHAR2(254),
    META_PAYLOAD BLOB,
    META_SER_ID NUMBER(10),
    META_SER_MANIFEST VARCHAR2(254))
/

ALTER TABLE EVENT_JOURNAL ADD CONSTRAINT EVENT_JOURNAL_PK PRIMARY KEY(PERSISTENCE_ID,SEQUENCE_NUMBER)
/

ALTER TABLE EVENT_JOURNAL ADD CONSTRAINT EVENT_JOURNAL_ORDERING_IDX UNIQUE(ORDERING)
/

CREATE SEQUENCE EVENT_JOURNAL__ORDERING_SEQ START WITH 1 INCREMENT BY 1
/

CREATE OR REPLACE TRIGGER EVENT_JOURNAL__ORDERING_TRG before insert on EVENT_JOURNAL referencing new as new for each row when (new.ORDERING is null) begin select EVENT_JOURNAL__ORDERING_seq.nextval into :new.ORDERING from sys.dual; end;
/

CREATE OR REPLACE TRIGGER EVENT_JOURNAL__ORDERING_TRG before insert on EVENT_JOURNAL referencing new as new for each row when (new.ORDERING is null) begin select EVENT_JOURNAL__ORDERING_seq.nextval into :new.ORDERING from sys.dual; end;
/

CREATE TABLE EVENT_TAG (
    EVENT_ID NUMERIC NOT NULL,
    TAG VARCHAR(255) NOT NULL,
    PRIMARY KEY(EVENT_ID, TAG),
    FOREIGN KEY(EVENT_ID) REFERENCES EVENT_JOURNAL(ORDERING)
    ON DELETE CASCADE
    )
/

CREATE TABLE "SNAPSHOT" (
    PERSISTENCE_ID VARCHAR(255) NOT NULL,
    SEQUENCE_NUMBER NUMERIC NOT NULL,
    CREATED NUMERIC NOT NULL,
    SNAPSHOT_SER_ID NUMBER(10) NOT NULL,
    SNAPSHOT_SER_MANIFEST VARCHAR(255),
    SNAPSHOT_PAYLOAD BLOB NOT NULL,
    META_SER_ID NUMBER(10),
    META_SER_MANIFEST VARCHAR(255),
    META_PAYLOAD BLOB)
/

ALTER TABLE "SNAPSHOT" ADD CONSTRAINT "SNAPSHOT_pk" PRIMARY KEY(PERSISTENCE_ID,SEQUENCE_NUMBER)
/

CREATE OR REPLACE PROCEDURE "reset_sequence"
IS
  l_value NUMBER;
BEGIN
  EXECUTE IMMEDIATE 'SELECT EVENT_JOURNAL__ORDERING_SEQ.nextval FROM dual' INTO l_value;
  EXECUTE IMMEDIATE 'ALTER SEQUENCE EVENT_JOURNAL__ORDERING_SEQ INCREMENT BY -' || l_value || ' MINVALUE 0';
  EXECUTE IMMEDIATE 'SELECT EVENT_JOURNAL__ORDERING_SEQ.nextval FROM dual' INTO l_value;
  EXECUTE IMMEDIATE 'ALTER SEQUENCE EVENT_JOURNAL__ORDERING_SEQ INCREMENT BY 1 MINVALUE 0';
END;
/