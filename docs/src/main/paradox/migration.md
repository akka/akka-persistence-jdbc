# Migration

## Migrating to version 5.4.0

Release `5.4.0` change the schema of `event_tag` table.

The previous version was using an auto-increment column as a primary key and foreign key on the `event_tag` table. As a result, the insert of multiple events in batch was not performant.

While in `5.4.0`, the primary key and foreign key on the `event_tag` table have been replaced with a primary key from the `event_journal` table. In order to migrate to the new schema, we made a [**migration script**](https://github.com/akka/akka-persistence-jdbc/tree/master/core/src/main/resources/schema) which is capable of creating the new column, migrate the rows and add the new constraints.

By default, the plugin will behave as in previous version. If you want to use the new `event_tag` keys, you need to run a multiple-phase rollout:

1. apply the first part of the migration script and then redeploy your application with the default settings.
2. apply the second part of the migration script that will migrate the rows and adapt the constraints.
3. redeploy the application by disabling the legacy-mode:

```config
jdbc-journal {
  tables {
    // ...
    event_tag {
      // ...
      // enable the new tag key
      legacy-tag-key = false
    } 
  }
}
// or simply configue via flatting style
jdbc-journal.tables.event_tag.legacy-tag-key = false
```


## Migrating to version 5.2.0

**Release `5.2.0` updates H2 to version 2.1.214 which is not compatible to the previous 1.4.200***

H2 has undergone considerable changes that broke backwards compatibility to make H2 SQL Standard compliant.
For migration please refer to the H2 [migration guide](https://www.h2database.com/html/migration-to-v2.html)


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

If you have re-configured the `schemaName`, `tableName` and `columnNames` through configuration settings then you will need to move them to a new key.

* key `jdbc-journal.tables.journal` becomes `jdbc-journal.tables.legacy_journal`
* key `jdbc-snapshot-store.tables.snapshot` becomes `jdbc-snapshot-store.tables.legacy_snapshot`
* key `jdbc-read-journal.tables.journal` becomes `jdbc-read-journal.tables.legacy_journal`
