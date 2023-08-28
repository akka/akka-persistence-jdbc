-- >>>>>>>>>>> before rolling updates.

ALTER TABLE event_tag
    ADD (PERSISTENCE_ID VARCHAR2(255),
         SEQUENCE_NUMBER NUMERIC);

-- >>>>>>>>>>> after projection catch up.
DELETE
FROM event_tag
WHERE PERSISTENCE_ID IS NULL
  AND SEQUENCE_NUMBER IS NULL;
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
ALTER TABLE event_tag
DROP PRIMARY KEY;
-- create new PK constraint for PK column.
ALTER TABLE event_tag
    ADD CONSTRAINT "pk_event_tag"
        PRIMARY KEY (PERSISTENCE_ID, SEQUENCE_NUMBER, TAG);
-- create new FK constraint for PK column.
ALTER TABLE event_tag
    ADD CONSTRAINT fk_event_journal_on_pk
        FOREIGN KEY (PERSISTENCE_ID, SEQUENCE_NUMBER)
            REFERENCES event_journal (PERSISTENCE_ID, SEQUENCE_NUMBER)
            ON DELETE CASCADE;
-- alter the event_id to nullable, so we can skip the InsertAndReturn.
ALTER TABLE event_tag
    MODIFY EVENT_ID NULL;