-- **************** first step ****************
-- add new column
ALTER TABLE public.event_tag
    ADD persistence_id  VARCHAR(255),
    ADD sequence_number BIGINT;
-- **************** second step ****************
-- migrate rows
UPDATE public.event_tag
SET persistence_id  = public.event_journal.persistence_id,
    sequence_number = public.event_journal.sequence_number
FROM event_journal
WHERE public.event_tag.event_id = public.event_journal.ordering;
-- drop old FK constraint
ALTER TABLE public.event_tag
DROP CONSTRAINT "fk_event_journal";
-- drop old PK  constraint
ALTER TABLE public.event_tag
DROP CONSTRAINT "event_tag_pkey";
-- create new PK constraint for PK column.
ALTER TABLE public.event_tag
    ADD CONSTRAINT "pk_event_tag"
        PRIMARY KEY (persistence_id, sequence_number, tag);
-- create new FK constraint for PK column.
ALTER TABLE public.event_tag
    ADD CONSTRAINT "fk_event_journal_on_pk"
        FOREIGN KEY (persistence_id, sequence_number)
            REFERENCES public.event_journal (persistence_id, sequence_number)
            ON DELETE CASCADE;
-- alter the event_id to nullable, so we can skip the InsertAndReturn.
ALTER TABLE public.event_tag
    ALTER COLUMN event_id DROP NOT NULL;