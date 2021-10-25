CREATE TABLE IF NOT EXISTS PUBLIC."journal" (
  "ordering" BIGINT AUTO_INCREMENT,
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" BIGINT NOT NULL,
  "deleted" BOOLEAN DEFAULT FALSE NOT NULL,
  "tags" VARCHAR(255) DEFAULT NULL,
  "message" BYTEA NOT NULL,
  PRIMARY KEY("persistence_id", "sequence_number")
);
CREATE UNIQUE INDEX IF NOT EXISTS  "journal_ordering_idx" ON PUBLIC."journal"("ordering");

CREATE TABLE IF NOT EXISTS PUBLIC."legacy_snapshot" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" BIGINT NOT NULL,
  "created" BIGINT NOT NULL,
  "snapshot" BYTEA NOT NULL,
  PRIMARY KEY("persistence_id", "sequence_number")
);


CREATE TABLE IF NOT EXISTS "durable_state" (
    "global_offset" BIGINT NOT NULL AUTO_INCREMENT,
    "persistence_id" VARCHAR(255) NOT NULL,
    "revision" BIGINT NOT NULL,
    "state_payload" BLOB NOT NULL,
    "state_serial_id" INTEGER NOT NULL,
    "state_serial_manifest" VARCHAR,
    "tag" VARCHAR,
    "state_timestamp" BIGINT NOT NULL,
    PRIMARY KEY("persistence_id")
    );

CREATE INDEX "state_tag_idx" on "durable_state" ("tag");
CREATE INDEX "state_global_offset_idx" on "durable_state" ("global_offset");
