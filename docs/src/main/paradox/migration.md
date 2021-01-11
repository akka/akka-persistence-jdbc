# Migration

## Migrating to version 5.0.0

**Release `5.0.0` introduces a new schema that is not compatible with older versions.** The schema is for new applications. No migration tool exists yet.

If you have existing data override the DAO to continue using the old schema:

```hocon
# Use the DAOs for the legacy (pre 5.0) database schema

jdbc-journal {
  dao = "akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalDao"
}

jdbc-snapshot-store {
  dao = "akka.persistence.jdbc.snapshot.dao.legacy.ByteArraySnapshotDao"
}

jdbc-read-journal {
  dao = "akka.persistence.jdbc.query.dao.legacy.ByteArrayReadJournalDao"
}
```

