/*
Note that because this schema uses VARCHAR (as opposed to NVARCHAR), it is recommended if using this schema to
disable sending string fields as Unicode.  This can be accomplished by appending
;sendStringParametersAsUnicode=false to the JDBC connection string.
*/

IF  NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'"journal"') AND type in (N'U'))
begin
CREATE TABLE journal (
  "ordering" BIGINT IDENTITY(1,1) NOT NULL,
  "deleted" BIT DEFAULT 0 NOT NULL,
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" NUMERIC(10,0) NOT NULL,
  "tags" VARCHAR(255) NULL DEFAULT NULL,
  "message" VARBINARY(max) NOT NULL,
  PRIMARY KEY ("persistence_id", "sequence_number")
)
CREATE UNIQUE INDEX journal_ordering_idx ON journal (ordering)
end;


IF  NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'"snapshot"') AND type in (N'U'))
CREATE TABLE snapshot (
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" NUMERIC(10,0) NOT NULL,
  "created" NUMERIC NOT NULL,
  "snapshot" VARBINARY(max) NOT NULL,
  PRIMARY KEY ("persistence_id", "sequence_number")
);
end;
