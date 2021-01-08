CREATE TABLE IF NOT EXISTS "event_journal" (
    "ordering" BIGINT NOT NULL AUTO_INCREMENT,
    "deleted" BOOLEAN DEFAULT false NOT NULL,
    "persistence_id" VARCHAR(255) NOT NULL,
    "sequence_number" BIGINT NOT NULL,
    "writer" VARCHAR NOT NULL,
    "write_timestamp" BIGINT NOT NULL,
    "adapter_manifest" VARCHAR NOT NULL,
    "event_payload" BLOB NOT NULL,
    "event_ser_id" INTEGER NOT NULL,
    "event_ser_manifest" VARCHAR NOT NULL,
    "meta_payload" BLOB,
    "meta_ser_id" INTEGER,
    "meta_ser_manifest" VARCHAR,
    PRIMARY KEY("persistence_id","sequence_number")
    );

CREATE UNIQUE INDEX "event_journal_ordering_idx" on "event_journal" ("ordering");

CREATE TABLE IF NOT EXISTS "event_tag" (
    "event_id" BIGINT NOT NULL,
    "tag" VARCHAR NOT NULL,
    PRIMARY KEY("event_id", "tag"),
    CONSTRAINT fk_event_journal
      FOREIGN KEY("event_id")
      REFERENCES "event_journal"("ordering")
      ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS "snapshot" (
    "persistence_id" VARCHAR(255) NOT NULL,
    "sequence_number" BIGINT NOT NULL,
    "created" BIGINT NOT NULL,"snapshot_ser_id" INTEGER NOT NULL,
    "snapshot_ser_manifest" VARCHAR NOT NULL,
    "snapshot_payload" BLOB NOT NULL,
    "meta_ser_id" INTEGER,
    "meta_ser_manifest" VARCHAR,
    "meta_payload" BLOB,
    PRIMARY KEY("persistence_id","sequence_number")
    );

