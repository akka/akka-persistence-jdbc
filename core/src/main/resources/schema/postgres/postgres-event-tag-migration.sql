-- >>>>>>>>>>> before rolling updates.

ALTER TABLE EVENT_TAG
    ADD PERSISTENCE_ID  VARCHAR(255),
    ADD SEQUENCE_NUMBER BIGINT;

-- >>>>>>>>>>> after projection catch up.
DELETE
FROM EVENT_TAG
WHERE PERSISTENCE_ID IS NULL
  AND SEQUENCE_NUMBER IS NULL;
-- drop old FK constraint
ALTER TABLE EVENT_TAG
DROP CONSTRAINT "fk_event_journal";
-- drop old PK  constraint
ALTER TABLE EVENT_TAG
DROP CONSTRAINT "event_tag_pkey";
-- create new PK constraint for PK column.
ALTER TABLE EVENT_TAG
    ADD CONSTRAINT "pk_event_tag"
        PRIMARY KEY (PERSISTENCE_ID, SEQUENCE_NUMBER, TAG);
-- create new FK constraint for PK column.
ALTER TABLE EVENT_TAG
    ADD CONSTRAINT "fk_event_journal_on_pk"
        FOREIGN KEY (PERSISTENCE_ID, SEQUENCE_NUMBER)
            REFERENCES EVENT_JOURNAL (PERSISTENCE_ID, SEQUENCE_NUMBER)
            ON DELETE CASCADE;
-- alter the event_id to nullable, so we can skip the InsertAndReturn.
ALTER TABLE EVENT_TAG
    ALTER COLUMN EVENT_ID DROP NOT NULL;