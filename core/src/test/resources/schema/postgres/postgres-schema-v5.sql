DROP TABLE IF EXISTS public.event_tag;
DROP TABLE IF EXISTS public.journal_event;

CREATE TABLE IF NOT EXISTS public.journal_event (
  ordering BIGSERIAL,
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  deleted BOOLEAN DEFAULT FALSE NOT NULL,

  writer VARCHAR(255) NOT NULL,
  write_timestamp BIGINT,
  event_manifest VARCHAR(255),

  event_ser_id INTEGER NOT NULL,
  event_ser_manifest VARCHAR(255) NOT NULL,
  event_payload BYTEA NOT NULL,

  meta_ser_id INTEGER,
  meta_ser_manifest VARCHAR(255),
  meta_payload BYTEA,

  PRIMARY KEY(persistence_id, sequence_number)
);

CREATE UNIQUE INDEX journal_event_ordering_idx ON public.journal_event(ordering);

CREATE TABLE IF NOT EXISTS public.event_tag(
    event_id BIGINT,
    tag VARCHAR(256),
    CONSTRAINT fk_journal_event
      FOREIGN KEY(event_id)
	  REFERENCES journal_event(ordering)
	  ON DELETE CASCADE
);

DROP TABLE IF EXISTS public.snapshot;

CREATE TABLE IF NOT EXISTS public.snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,
  snapshot BYTEA NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);

