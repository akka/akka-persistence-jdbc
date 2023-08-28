-- >>>>>>>>>>> before rolling updates.

ALTER TABLE event_tag
    ADD PERSISTENCE_ID  VARCHAR(255),
    ADD SEQUENCE_NUMBER BIGINT;

-- >>>>>>>>>>> after projection catch up.
DELETE
FROM event_tag
WHERE PERSISTENCE_ID IS NULL
  AND SEQUENCE_NUMBER IS NULL;
-- drop old FK constraint
SELECT CONSTRAINT_NAME
INTO @fk_constraint_name
FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
WHERE TABLE_NAME = 'event_tag';
SET @alter_query = CONCAT('ALTER TABLE event_tag DROP FOREIGN KEY ', @fk_constraint_name);
PREPARE stmt FROM @alter_query;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
-- drop old PK  constraint
ALTER TABLE event_tag
DROP PRIMARY KEY;
-- create new PK constraint for PK column.
ALTER TABLE event_tag
    ADD CONSTRAINT
        PRIMARY KEY (PERSISTENCE_ID, SEQUENCE_NUMBER, TAG);
-- create new FK constraint for PK column.
ALTER TABLE event_tag
    ADD CONSTRAINT fk_event_journal_on_pk
        FOREIGN KEY (PERSISTENCE_ID, SEQUENCE_NUMBER)
            REFERENCES event_journal (PERSISTENCE_ID, SEQUENCE_NUMBER)
            ON DELETE CASCADE;
-- alter the event_id to nullable, so we can skip the InsertAndReturn.
ALTER TABLE event_tag
    MODIFY COLUMN EVENT_ID BIGINT UNSIGNED NULL;