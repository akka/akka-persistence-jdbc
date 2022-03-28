/*
Akka Persistence JDBC versions from 5.0.0 through 5.0.4 used this schema.  The only difference from the
post-5.0.4 schema is the use of VARCHAR instead of NVARCHAR for string fields.  It is strongly
recommended that new uses of Akka Persistence JDBC 5.0.0 and later use the NVARCHAR schema.  This schema is
still usable with post-5.0.4 versions of Akka Persistence JDBC, though will not support Unicode persistence IDs,
manifests, or tags.

Additionally, if using this schema, it is highly recommended to not have the SQL Server JDBC client send
strings as Unicode, by appending ;sendStringParametersAsUnicode=false to the JDBC connection string.
*/

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

