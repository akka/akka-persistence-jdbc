-- **************** first step ****************
-- add new column
ALTER TABLE event_tag
    ADD persistence_id  VARCHAR(255),
    ADD sequence_number BIGINT;
-- **************** second step ****************
-- migrate rows
UPDATE event_tag
INNER JOIN event_journal ON event_tag.event_id = event_journal.ordering
SET event_tag.persistence_id = event_journal.persistence_id,
    event_tag.sequence_number = event_journal.sequence_number;
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
        PRIMARY KEY (persistence_id, sequence_number, tag);
-- create new FK constraint for PK column.
ALTER TABLE event_tag
    ADD CONSTRAINT fk_event_journal_on_pk
        FOREIGN KEY (persistence_id, sequence_number)
            REFERENCES event_journal (persistence_id, sequence_number)
            ON DELETE CASCADE;
-- alter the event_id to nullable, so we can skip the InsertAndReturn.
ALTER TABLE event_tag
    MODIFY COLUMN event_id BIGINT UNSIGNED NULL;