CREATE TABLE IF NOT EXISTS public.migrated2 (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,
  snapshot BYTEA NOT NULL
);
CREATE TABLE test_user (
  name VARCHAR(200)
);
