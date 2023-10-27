-- **************** first step ****************
-- add new column
ALTER TABLE EVENT_TAG
    ADD (PERSISTENCE_ID VARCHAR2(255),
         SEQUENCE_NUMBER NUMERIC);
-- **************** second step ****************
-- migrate rows
UPDATE EVENT_TAG
SET PERSISTENCE_ID  = (SELECT PERSISTENCE_ID
                       FROM EVENT_JOURNAL
                       WHERE EVENT_TAG.EVENT_ID = EVENT_JOURNAL.ORDERING),
    SEQUENCE_NUMBER = (SELECT SEQUENCE_NUMBER
                       FROM EVENT_JOURNAL
                       WHERE EVENT_TAG.EVENT_ID = EVENT_JOURNAL.ORDERING)
-- drop old FK constraint
DECLARE
v_constraint_name VARCHAR2(255);
BEGIN
SELECT CONSTRAINT_NAME
INTO v_constraint_name
FROM USER_CONSTRAINTS
WHERE TABLE_NAME = 'EVENT_TAG'
  AND CONSTRAINT_TYPE = 'R';

IF v_constraint_name IS NOT NULL THEN
        EXECUTE IMMEDIATE 'ALTER TABLE EVENT_TAG DROP CONSTRAINT ' || v_constraint_name;
END IF;

COMMIT;
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        RAISE;
END;
/

-- drop old PK  constraint
ALTER TABLE EVENT_TAG
DROP PRIMARY KEY;
-- create new PK constraint for PK column.
ALTER TABLE EVENT_TAG
    ADD CONSTRAINT "pk_event_tag"
        PRIMARY KEY (PERSISTENCE_ID, SEQUENCE_NUMBER, TAG);
-- create new FK constraint for PK column.
ALTER TABLE EVENT_TAG
    ADD CONSTRAINT fk_EVENT_JOURNAL_on_pk
        FOREIGN KEY (PERSISTENCE_ID, SEQUENCE_NUMBER)
            REFERENCES EVENT_JOURNAL (PERSISTENCE_ID, SEQUENCE_NUMBER)
            ON DELETE CASCADE;
-- alter the EVENT_ID to nullable, so we can skip the InsertAndReturn.
ALTER TABLE EVENT_TAG
    MODIFY EVENT_ID NULL;