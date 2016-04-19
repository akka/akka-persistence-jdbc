DROP table "journal";

CREATE TABLE "journal" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" NUMERIC NOT NULL,
  "created" NUMERIC NOT NULL,
  "tags" VARCHAR(255) DEFAULT NULL,
  "message" BLOB NOT NULL,
  PRIMARY KEY("persistence_id", "sequence_number")
);

DROP table "deleted_to";

CREATE TABLE "deleted_to" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "deleted_to" NUMERIC NOT NULL
);

DROP table "snapshot";

CREATE TABLE "snapshot" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" NUMERIC NOT NULL,
  "created" NUMERIC NOT NULL,
  "snapshot" BLOB NOT NULL,
  PRIMARY KEY ("persistence_id", "sequence_number")
);