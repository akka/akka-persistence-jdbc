ALTER TABLE "journal" MODIFY "message" BLOB DEFAULT NULL;
ALTER TABLE "journal" ADD (
  "event" BLOB DEFAULT NULL,
  "event_manifest" VARCHAR(255) DEFAULT NULL,
  "ser_id" INTEGER DEFAULT NULL,
  "ser_manifest" VARCHAR(255) DEFAULT NULL,
  "writer_uuid" VARCHAR(36) DEFAULT NULL
);

ALTER TABLE "snapshot" MODIFY "snapshot" BLOB DEFAULT NULL;
ALTER TABLE "snapshot" ADD (
  "snapshot_data" BLOB DEFAULT NULL,
  "ser_id" INTEGER DEFAULT NULL,
  "ser_manifest" VARCHAR(255) DEFAULT NULL
);
