DROP TABLE IF EXISTS event_tag;
DROP TABLE IF EXISTS journal_event;

CREATE TABLE IF NOT EXISTS journal_event (
    ordering SERIAL,
    deleted BOOLEAN DEFAULT false NOT NULL,
    persistence_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    writer TEXT NOT NULL,
    write_timestamp BIGINT NOT NULL,
    event_manifest TEXT NOT NULL,
    event_payload BLOB NOT NULL,
    event_ser_id INTEGER NOT NULL,
    event_ser_manifest TEXT NOT NULL,
    meta_payload BLOB,
    meta_ser_id INTEGER,meta_ser_manifest TEXT,
    PRIMARY KEY(persistence_id,sequence_number)
);

CREATE UNIQUE INDEX journal_event_ordering_idx ON journal_event (ordering);

CREATE TABLE IF NOT EXISTS event_tag (
    event_id BIGINT UNSIGNED NOT NULL,
    tag VARCHAR(255) NOT NULL,
    primary key(event_id, tag),
        FOREIGN KEY (event_id)
        REFERENCES journal_event(ordering)
        ON DELETE CASCADE
    );

DROP TABLE IF EXISTS snapshot;

CREATE TABLE IF NOT EXISTS snapshot (
    persistence_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    created BIGINT NOT NULL,
    snapshot_ser_id INTEGER NOT NULL,
    snapshot_ser_manifest TEXT NOT NULL,
    snapshot_payload BLOB NOT NULL,
    meta_ser_id INTEGER,
    meta_ser_manifest TEXT,
    meta_payload BLOB,
  PRIMARY KEY (persistence_id, sequence_number));
