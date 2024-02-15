-- **************** first step ****************
-- add new column
ALTER TABLE event_tag
    ADD persistence_id  VARCHAR(255),
    ADD sequence_number BIGINT;
-- **************** second step ****************
-- migrate rows
UPDATE event_tag
SET persistence_id  = event_journal.persistence_id,
    sequence_number = event_journal.sequence_number
FROM event_journal
WHERE event_tag.event_id = event_journal.ordering;
-- drop old FK constraint
DECLARE @fkConstraintName NVARCHAR(MAX);
DECLARE @dropFKConstraintQuery NVARCHAR(MAX);

SELECT @fkConstraintName = CONSTRAINT_NAME
FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
WHERE TABLE_NAME = 'event_tag'
  AND CONSTRAINT_TYPE = 'FOREIGN KEY';

IF @fkConstraintName IS NOT NULL
BEGIN
        SET @dropFKConstraintQuery = 'ALTER TABLE event_tag DROP CONSTRAINT ' + QUOTENAME(@fkConstraintName);
EXEC sp_executesql @dropFKConstraintQuery;
END
-- drop old PK  constraint
DECLARE @constraintName NVARCHAR(MAX);
DECLARE @dropConstraintQuery NVARCHAR(MAX);

SELECT @constraintName = CONSTRAINT_NAME
FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
WHERE TABLE_NAME = 'event_tag'
  AND CONSTRAINT_TYPE = 'PRIMARY KEY';

IF @constraintName IS NOT NULL
BEGIN
        SET @dropConstraintQuery = 'ALTER TABLE event_tag DROP CONSTRAINT ' + QUOTENAME(@constraintName);
EXEC sp_executesql @dropConstraintQuery;
END
-- create new PK constraint for PK column.
ALTER TABLE event_tag
ALTER COLUMN persistence_id NVARCHAR(255) NOT NULL
ALTER TABLE event_tag
ALTER COLUMN sequence_number NUMERIC(10, 0) NOT NULL
ALTER TABLE event_tag
    ADD CONSTRAINT "pk_event_tag"
        PRIMARY KEY (persistence_id, sequence_number, TAG)
-- create new FK constraint for PK column.
ALTER TABLE event_tag
    ADD CONSTRAINT "fk_event_journal_on_pk"
        FOREIGN KEY (persistence_id, sequence_number)
            REFERENCES event_journal (persistence_id, sequence_number)
            ON DELETE CASCADE
-- alter the event_id to nullable, so we can skip the InsertAndReturn.
ALTER TABLE event_tag
ALTER COLUMN event_id BIGINT NULL