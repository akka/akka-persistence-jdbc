DROP TABLE IF EXISTS journal;

CREATE TABLE journal (
	"ordering" BIGINT IDENTITY(1,1) NOT NULL,
	"persistence_id" VARCHAR(255) NOT NULL,
	"sequence_number" NUMERIC(10,0) NOT NULL,
	"deleted" BIT NULL DEFAULT 0,
	"tags" VARCHAR(255) NULL DEFAULT NULL,
	"event" VARBINARY(max) NOT NULL,
	"event_manifest" VARCHAR(255) NOT NULL,
	"ser_id" INTEGER NOT NULL,
	"ser_manifest" VARCHAR(255) NOT NULL,
	"writer_uuid" VARCHAR(36) NOT NULL,
	PRIMARY KEY ("persistence_id", "sequence_number")
);

CREATE UNIQUE INDEX journal_ordering_idx ON journal (ordering);

DROP TABLE IF EXISTS snapshot;

CREATE TABLE snapshot (
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" NUMERIC(10,0) NOT NULL,
  "created" NUMERIC NOT NULL,
  "snapshot_data" VARBINARY(max) NOT NULL,
  "ser_id" INTEGER NOT NULL,
  "ser_manifest" VARCHAR(255) NOT NULL,
  PRIMARY KEY ("persistence_id", "sequence_number")
);
