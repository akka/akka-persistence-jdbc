CREATE SEQUENCE "ordering_seq" START WITH 1 INCREMENT BY 1 NOMAXVALUE
/

CREATE TABLE "journal" (
  "ordering" NUMERIC,
  "deleted" char check ("deleted" in (0,1)),
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" NUMERIC NOT NULL,
  "tags" VARCHAR(255) DEFAULT NULL,
  "event" BLOB,
  "event_manifest" VARCHAR(255),
  "ser_id" INTEGER NOT NULL,
  "ser_manifest" VARCHAR(255),
  "writer_uuid" VARCHAR(36),
  PRIMARY KEY("persistence_id", "sequence_number")
)
/

CREATE UNIQUE INDEX "journal_ordering_idx" ON "journal"("ordering")
/

CREATE OR REPLACE TRIGGER "ordering_seq_trigger"
BEFORE INSERT ON "journal"
FOR EACH ROW
BEGIN
  SELECT "ordering_seq".NEXTVAL INTO :NEW."ordering" FROM DUAL;
END;
/

CREATE OR REPLACE PROCEDURE "reset_sequence"
IS
  l_value NUMBER;
BEGIN
  EXECUTE IMMEDIATE 'SELECT "ordering_seq".nextval FROM dual' INTO l_value;
  EXECUTE IMMEDIATE 'ALTER SEQUENCE "ordering_seq" INCREMENT BY -' || l_value || ' MINVALUE 0';
  EXECUTE IMMEDIATE 'SELECT "ordering_seq".nextval FROM dual' INTO l_value;
  EXECUTE IMMEDIATE 'ALTER SEQUENCE "ordering_seq" INCREMENT BY 1 MINVALUE 0';
END;
/

CREATE TABLE "snapshot" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" NUMERIC NOT NULL,
  "created" NUMERIC NOT NULL,
  "snapshot_data" BLOB,
  "ser_id" INTEGER NOT NULL,
  "ser_manifest" VARCHAR(255),
  PRIMARY KEY ("persistence_id", "sequence_number")
)
/