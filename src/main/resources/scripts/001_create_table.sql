CREATE TABLE public.event_store (
  processor_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  marker VARCHAR(255) NOT NULL,
  message TEXT NOT NULL,
  created TIMESTAMP NOT NULL,
  PRIMARY KEY(processor_id, sequence_number)
);