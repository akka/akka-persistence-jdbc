DROP TABLE IF EXISTS PUBLIC."journal";

CREATE TABLE IF NOT EXISTS PUBLIC."journal" (
  "ordering" BIGINT AUTO_INCREMENT,
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" BIGINT NOT NULL,
  "deleted" BOOLEAN DEFAULT FALSE,
  "tags" VARCHAR(255) DEFAULT NULL,
  "message" BYTEA DEFAULT NULL,
  "event" BYTEA NOT NULL,
  "event_manifest" VARCHAR(255) NOT NULL,
  "ser_id" INTEGER NOT NULL,
  "ser_manifest" VARCHAR(255) NOT NULL,
  "writer_uuid" VARCHAR(36) NOT NULL,
  PRIMARY KEY("persistence_id", "sequence_number")
);

CREATE UNIQUE INDEX "journal_ordering_idx" ON PUBLIC."journal"("ordering");

DROP TABLE IF EXISTS PUBLIC."snapshot";

CREATE TABLE IF NOT EXISTS PUBLIC."snapshot" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" BIGINT NOT NULL,
  "created" BIGINT NOT NULL,
  "snapshot" BYTEA DEFAULT NULL,
  "snapshot_data" BYTEA NOT NULL,
  "ser_id" INTEGER NOT NULL,
  "ser_manifest" VARCHAR(255) NOT NULL,
  PRIMARY KEY("persistence_id", "sequence_number")
);
