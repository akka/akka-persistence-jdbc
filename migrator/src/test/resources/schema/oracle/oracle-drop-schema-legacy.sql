-- (ddl lock timeout in seconds) this allows tests which are still writing to the db to finish gracefully
ALTER SESSION SET ddl_lock_timeout = 150
/

DROP TABLE "journal" CASCADE CONSTRAINT
/

DROP TABLE "legacy_snapshot" CASCADE CONSTRAINT
/

DROP TABLE "deleted_to" CASCADE CONSTRAINT
/

DROP TRIGGER "ordering_seq_trigger"
/

DROP PROCEDURE "reset_sequence"
/

DROP SEQUENCE "ordering_seq"
/
