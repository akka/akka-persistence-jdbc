CREATE SEQUENCE "ordering_seq" start with 1 increment by 1 NOMAXVALUE;

CREATE TABLE "journal" (
  "ordering" NUMERIC,
  "deleted" char check ("deleted" in (0,1)),
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" NUMERIC NOT NULL,
  "created" NUMERIC NOT NULL,
  "tags" VARCHAR(255) DEFAULT NULL,
  "message" BLOB NOT NULL,
  PRIMARY KEY("ordering", "persistence_id", "sequence_number")
);

CREATE TRIGGER "ordering_seq_trigger"
BEFORE INSERT ON "journal"
FOR EACH ROW
BEGIN
  select ordering_seq.nextval into :new.ordering from dual;
END;

CREATE TABLE "deleted_to" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "deleted_to" NUMERIC NOT NULL
);

CREATE TABLE "snapshot" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" NUMERIC NOT NULL,
  "created" NUMERIC NOT NULL,
  "snapshot" BLOB NOT NULL,
  PRIMARY KEY ("persistence_id", "sequence_number")
);