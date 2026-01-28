# Persistence Query

## How to get the ReadJournal

The `ReadJournal` is retrieved via the `akka.persistence.query.PersistenceQuery` extension:

Scala
:  @@snip[snip](/core/src/test/scala/akka/persistence/jdbc/ScaladslSnippets.scala) { #read-journal }

Java
: @@snip[snip](/core/src/test/java/akka/persistence/jdbc/JavadslSnippets.java) { #read-journal }

## Persistence Query Plugin

The plugin supports the following queries:

## AllPersistenceIdsQuery and CurrentPersistenceIdsQuery

`allPersistenceIds` and `currentPersistenceIds` are used for retrieving all persistenceIds of all persistent actors.

Scala
:  @@snip[snip](/core/src/test/scala/akka/persistence/jdbc/ScaladslSnippets.scala) { #persistence-ids }

Java
:  @@snip[snip](/core/src/test/java/akka/persistence/jdbc/JavadslSnippets.java) { #persistence-ids }

The returned event stream is unordered and you can expect different order for multiple executions of the query.

When using the `persistenceIds` query, the stream is not completed when it reaches the end of the currently used persistenceIds,
but it continues to push new persistenceIds when new persistent actors are created.

When using the `currentPersistenceIds` query, the stream is completed when the end of the current list of persistenceIds is reached,
thus it is not a `live` query.

The stream is completed with failure if there is a failure in executing the query in the backend journal.

## EventsByPersistenceIdQuery and CurrentEventsByPersistenceIdQuery

`eventsByPersistenceId` and `currentEventsByPersistenceId` is used for retrieving events for
a specific PersistentActor identified by persistenceId.

Scala
:  @@snip[snip](/core/src/test/scala/akka/persistence/jdbc/ScaladslSnippets.scala) { #events-by-persistence-id }

Java
:  @@snip[snip](/core/src/test/java/akka/persistence/jdbc/JavadslSnippets.java) { #events-by-persistence-id }

You can retrieve a subset of all events by specifying `fromSequenceNr` and `toSequenceNr` or use `0L` and `Long.MaxValue` respectively to retrieve all events. Note that the corresponding sequence number of each event is provided in the `EventEnvelope`, which makes it possible to resume the stream at a later point from a given sequence number.

The returned event stream is ordered by sequence number, i.e. the same order as the PersistentActor persisted the events. The same prefix of stream elements (in same order) are returned for multiple executions of the query, except for when events have been deleted.

The stream is completed with failure if there is a failure in executing the query in the backend journal.

## EventsByTag and CurrentEventsByTag

`eventsByTag` and `currentEventsByTag` are used for retrieving events that were marked with a given
`tag`, e.g. all domain events of an Aggregate Root type.

Scala
:  @@snip[snip](/core/src/test/scala/akka/persistence/jdbc/ScaladslSnippets.scala) { #events-by-tag }

Java
:  @@snip[snip](/core/src/test/java/akka/persistence/jdbc/JavadslSnippets.java) { #events-by-tag }

### Performance

If you see slow database queries for `eventsByTag`, please consider adding a dedicated index for the `tag` column in the `event_tag` table.

For postgres, the following index can be used:

```
CREATE INDEX CONCURRENTLY event_tag_tag_idx ON public.event_tag (tag);
```