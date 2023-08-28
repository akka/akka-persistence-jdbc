CREATE TABLE event_journal (
    "ordering" BIGINT IDENTITY(1,1) NOT NULL,
    "deleted" BIT DEFAULT 0 NOT NULL,
    "persistence_id" NVARCHAR(255) NOT NULL,
    "sequence_number" NUMERIC(10,0) NOT NULL,
    "writer" NVARCHAR(255) NOT NULL,
    "write_timestamp" BIGINT NOT NULL,
    "adapter_manifest" NVARCHAR(MAX) NOT NULL,
    "event_payload" VARBINARY(MAX) NOT NULL,
    "event_ser_id" INTEGER NOT NULL,
    "event_ser_manifest" NVARCHAR(MAX) NOT NULL,
    "meta_payload" VARBINARY(MAX),
    "meta_ser_id" INTEGER,
    "meta_ser_manifest" NVARCHAR(MAX)
    PRIMARY KEY ("persistence_id", "sequence_number")
);

CREATE UNIQUE INDEX event_journal_ordering_idx ON event_journal(ordering);

CREATE TABLE event_tag (
    "event_id" BIGINT,
    "persistence_id" NVARCHAR(255),
    "sequence_number" NUMERIC(10,0),
    "tag" NVARCHAR(255) NOT NULL
    PRIMARY KEY ("persistence_id", "sequence_number","tag"),
    constraint "fk_event_journal"
        foreign key("persistence_id", "sequence_number")
        references "dbo"."event_journal"("persistence_id", "sequence_number")
        on delete CASCADE
);

CREATE TABLE "snapshot" (
    "persistence_id" NVARCHAR(255) NOT NULL,
    "sequence_number" NUMERIC(10,0) NOT NULL,
    "created" BIGINT NOT NULL,
    "snapshot_ser_id" INTEGER NOT NULL,
    "snapshot_ser_manifest" NVARCHAR(255) NOT NULL,
    "snapshot_payload" VARBINARY(MAX) NOT NULL,
    "meta_ser_id" INTEGER,
    "meta_ser_manifest" NVARCHAR(255),
    "meta_payload" VARBINARY(MAX),
    PRIMARY KEY ("persistence_id", "sequence_number")
  )

