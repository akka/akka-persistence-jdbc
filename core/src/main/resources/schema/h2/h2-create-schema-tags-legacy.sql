CREATE TABLE IF NOT EXISTS "event_tag" (
    "event_id" BIGINT NOT NULL,
    "tag" VARCHAR NOT NULL,
    PRIMARY KEY("event_id", "tag"),
    CONSTRAINT fk_event_journal
      FOREIGN KEY("event_id")
      REFERENCES "event_journal"("ordering")
      ON DELETE CASCADE
);