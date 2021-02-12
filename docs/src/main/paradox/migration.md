# Migration

## Migrating to version 5.0.0

**Release `5.0.0` introduces a new schema and serialization that is not compatible with older versions.** 

The previous version was wrapping the event payload with Akka's `PersistentRepr`, while in 5.0.0 the serialized event payload is persisted directly into the column. In order to migrate to the new schema, a migration tool capable of reading the serialized representation of `PersistentRepr` is required. That [tool doesn't exist yet](https://github.com/akka/akka-persistence-jdbc/issues/317), therefore, the new schema can only be used with new applications.

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

If you have re-configured the `schemaName`, `tableName` and `columnNames` through configuration settings, you will need to move them a new key.

* key `jdbc-journal.tables.journal` becomes `jdbc-journal.tables.legacy_journal`
* key `jdbc-snapshot-store.tables.snapshot` becomes `jdbc-snapshot-store.tables.legacy_snapshot`
* key `jdbc-read-journal.tables.journal` becomes `jdbc-read-journal.tables.legacy_journal`
