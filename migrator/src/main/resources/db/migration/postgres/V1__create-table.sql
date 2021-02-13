-- creation of the new akka persistence jdbc schemas

-- event_journal table
CREATE TABLE IF NOT EXISTS ${apj:event_journal}
(
    ordering           BIGSERIAL,
    persistence_id     VARCHAR(255)          NOT NULL,
    sequence_number    BIGINT                NOT NULL,
    deleted            BOOLEAN DEFAULT FALSE NOT NULL,

    writer             VARCHAR(255)          NOT NULL,
    write_timestamp    BIGINT,
    adapter_manifest   VARCHAR(255),

    event_ser_id       INTEGER               NOT NULL,
    event_ser_manifest VARCHAR(255)          NOT NULL,
    event_payload      BYTEA                 NOT NULL,

    meta_ser_id        INTEGER,
    meta_ser_manifest  VARCHAR(255),
    meta_payload       BYTEA,

    PRIMARY KEY (persistence_id, sequence_number)
);

CREATE UNIQUE INDEX ${apj:event_journal}_ordering_idx ON ${apj:event_journal} (ordering);

-- event_tag table
CREATE TABLE IF NOT EXISTS ${apj:event_tag}
(
    event_id BIGINT,
    tag      VARCHAR(256),
    PRIMARY KEY (event_id, tag),
    CONSTRAINT fk_${apj:event_journal}
        FOREIGN KEY (event_id)
            REFERENCES ${apj:event_journal} (ordering)
            ON DELETE CASCADE
);

-- state_snapshot table
CREATE TABLE IF NOT EXISTS ${apj:state_snapshot}
(
    persistence_id        VARCHAR(255) NOT NULL,
    sequence_number       BIGINT       NOT NULL,
    created               BIGINT       NOT NULL,

    snapshot_ser_id       INTEGER      NOT NULL,
    snapshot_ser_manifest VARCHAR(255) NOT NULL,
    snapshot_payload      BYTEA        NOT NULL,

    meta_ser_id           INTEGER,
    meta_ser_manifest     VARCHAR(255),
    meta_payload          BYTEA,

    PRIMARY KEY (persistence_id, sequence_number)
);
