CREATE TABLE event_journal(
    "ordering" BIGINT IDENTITY(1,1) NOT NULL,
    "deleted" BIT DEFAULT 0 NOT NULL,
    "persistence_id" VARCHAR(255) NOT NULL,
    "sequence_number" NUMERIC(10,0) NOT NULL,
    "writer" VARCHAR(255) NOT NULL,
    "write_timestamp" BIGINT NOT NULL,
    "adapter_manifest" VARCHAR(MAX) NOT NULL,
    "event_payload" VARBINARY(MAX) NOT NULL,
    "event_ser_id" INTEGER NOT NULL,
    "event_ser_manifest" VARCHAR(MAX) NOT NULL,
    "meta_payload" VARBINARY(MAX),
    "meta_ser_id" INTEGER,
    "meta_ser_manifest" VARCHAR(MAX)
    PRIMARY KEY ("persistence_id", "sequence_number")
);

CREATE UNIQUE INDEX event_journal_ordering_idx ON event_journal(ordering);

CREATE TABLE event_tag (
    "event_id" BIGINT NOT NULL,
    "tag" VARCHAR(255) NOT NULL
    PRIMARY KEY ("event_id","tag")
    constraint "fk_event_journal"
        foreign key("event_id")
        references "dbo"."event_journal"("ordering")
        on delete CASCADE
);

CREATE TABLE "snapshot" (
    "persistence_id" VARCHAR(255) NOT NULL,
    "sequence_number" NUMERIC(10,0) NOT NULL,
    "created" BIGINT NOT NULL,
    "snapshot_ser_id" INTEGER NOT NULL,
    "snapshot_ser_manifest" VARCHAR(255) NOT NULL,
    "snapshot_payload" VARBINARY(MAX) NOT NULL,
    "meta_ser_id" INTEGER,
    "meta_ser_manifest" VARCHAR(255),
    "meta_payload" VARBINARY(MAX),
    PRIMARY KEY ("persistence_id", "sequence_number")
  )

