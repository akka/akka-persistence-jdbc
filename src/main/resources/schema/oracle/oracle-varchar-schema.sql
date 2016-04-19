DROP TABLE "journal";

CREATE TABLE "journal" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" NUMERIC NOT NULL,
  "created" NUMERIC NOT NULL,
  "tags" VARCHAR(255) DEFAULT NULL,
  "message" CLOB NOT NULL,
  PRIMARY KEY("persistence_id", "sequence_number")
);

DROP TABLE "deleted_to";

CREATE TABLE "deleted_to" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "deleted_to" NUMERIC NOT NULL
);

DROP table "snapshot";

CREATE TABLE "snapshot" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" NUMERIC NOT NULL,
  "created" NUMERIC NOT NULL,
  "snapshot" CLOB NOT NULL,
  PRIMARY KEY ("persistence_id", "sequence_number")
);