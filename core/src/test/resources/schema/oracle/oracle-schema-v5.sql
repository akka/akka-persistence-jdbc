CREATE SEQUENCE "event_journal__ordering_seq" start with 1 increment by 1 NOMAXVALUE
/

CREATE TABLE "event_journal" (
    "ordering" NUMERIC,
    "deleted" CHAR(1) DEFAULT 0 NOT NULL check ("deleted" in (0, 1)),
    "persistence_id" VARCHAR(255) NOT NULL,
    "sequence_number" NUMERIC NOT NULL,
    "writer" VARCHAR(255) NOT NULL,
    "write_timestamp" NUMBER(19) NOT NULL,
    "event_manifest" VARCHAR(255) NOT NULL,
    "event_payload" BLOB NOT NULL,
    "event_ser_id" NUMERIC NOT NULL,
    "event_ser_manifest" VARCHAR(255) NOT NULL,
    "meta_payload" BLOB,
    "meta_ser_id" NUMERIC,
    "meta_ser_manifest" VARCHAR(255),
    PRIMARY KEY("persistence_id", "sequence_number")
    )
/

ALTER TABLE "event_journal" ADD CONSTRAINT "event_journal_ordering_idx" UNIQUE("ordering")
/

create or replace trigger "event_journal__ordering_trg"
    before insert on "event_journal" referencing new as new for each row when (new."ordering" is null)
    begin select "event_journal__ordering_seq".nextval into :new."ordering" from sys.dual; end;
/

CREATE TABLE "event_tag" (
    "event_id" NUMERIC NOT NULL,
    "tag" VARCHAR(255) NOT NULL
    )
/

alter table "event_tag" add constraint "event_tag_pk" primary key("event_id","tag")
/

alter table "event_tag" add constraint "fk_event_journal" foreign key("event_id") references "event_journal"("ordering")
/

CREATE TABLE "snapshot" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" NUMERIC NOT NULL,
  "created" NUMERIC NOT NULL,
  "snapshot" BLOB NOT NULL,
  PRIMARY KEY ("persistence_id", "sequence_number")
)
/

