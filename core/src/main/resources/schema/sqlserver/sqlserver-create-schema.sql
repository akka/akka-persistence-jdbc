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

-- The following indexes are recommended for performance of eventsByTag queries.
-- Use the one that matches your legacy-tag-key configuration.
-- For legacy-tag-key = false:
-- CREATE INDEX event_tag_tag_composite_idx ON event_tag ("tag", "persistence_id", "sequence_number");
-- For legacy-tag-key = true:
-- CREATE INDEX event_tag_tag_event_id_idx ON event_tag ("tag", "event_id");

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
