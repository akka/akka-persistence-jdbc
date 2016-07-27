DROP TABLE IF EXISTS journal;

CREATE TABLE IF NOT EXISTS journal (
  ordering BIGINT AUTO_INCREMENT,
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  deleted BOOLEAN DEFAULT FALSE,
  tags VARCHAR(255) DEFAULT NULL,
  message BLOB NOT NULL,
  PRIMARY KEY(ordering, persistence_id, sequence_number)
);

CREATE INDEX journal_persistence_id_sequence_number_idx ON journal(persistence_id, sequence_number);

DROP TABLE IF EXISTS snapshot;

CREATE TABLE IF NOT EXISTS snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,
  snapshot BLOB NOT NULL,
  PRIMARY KEY (persistence_id, sequence_number)
);
