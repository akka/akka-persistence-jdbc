# DurableStateStore
## How to get the DurableStateStore

The `DurableStateStore` for JDBC plugin is obtained through the `DurableStateStoreRegistry` extension.

Scala
:  @@snip[snip](/core/src/test/scala/akka/persistence/jdbc/state/ScaladslSnippets.scala) { #jdbc-durable-state-store }

Java
: @@snip[snip](/core/src/test/java/akka/persistence/jdbc/state/JavadslSnippets.java) { #jdbc-durable-state-store }

## APIs supported by DurableStateStore

The plugin supports the following APIs:

### getObject

`getObject(persistenceId)` returns `GetObjectResult(value, revision)`, where `value` is an `Option` (`Optional` in Java)
and is set to the value of the object if it exists with the passed in `persistenceId`. Otherwise `value` is empty.

Scala
:  @@snip[snip](/core/src/test/scala/akka/persistence/jdbc/state/ScaladslSnippets.scala) { #get-object }

Java
: @@snip[snip](/core/src/test/java/akka/persistence/jdbc/state/JavadslSnippets.java) { #get-object }

### upsertObject

`upsertObject(persistenceId, revision, value, tag)` inserts the record if the `persistenceId` does not exist in the 
database. Or else it updates the record with the latest revision passed as `revision`. The update succeeds only if the
incoming `revision` is 1 more than the already existing one. This snippet is an example of a sequnece of `upsertObject`
and `getObject`.

Scala
:  @@snip[snip](/core/src/test/scala/akka/persistence/jdbc/state/ScaladslSnippets.scala) { #upsert-get-object }

Java
: @@snip[snip](/core/src/test/java/akka/persistence/jdbc/state/JavadslSnippets.java) { #upsert-get-object }

### deleteObject

`deleteObject(persistenceId)` deletes the record with the input `persistenceId`.

Scala
:  @@snip[snip](/core/src/test/scala/akka/persistence/jdbc/state/ScaladslSnippets.scala) { #delete-object }

Java
: @@snip[snip](/core/src/test/java/akka/persistence/jdbc/state/JavadslSnippets.java) { #delete-object }

### currentChanges

`currentChanges(tag, offset)` gets a source of the most recent changes made to objects with the given `tag` since 
the passed in `offset`. This api returns changes that occurred up to when the `Source` returned by this call is materialized.

Scala
:  @@snip[snip](/core/src/test/scala/akka/persistence/jdbc/state/ScaladslSnippets.scala) { #current-changes }

Java
: @@snip[snip](/core/src/test/java/akka/persistence/jdbc/state/JavadslSnippets.java) { #current-changes }

### changes

`changes(tag, offset)` gets a source of the most recent changes made to objects with the given `tag` since 
the passed in `offset`. The returned source will never terminate, it effectively watches for changes to the objects 
and emits changes as they happen.

Scala
:  @@snip[snip](/core/src/test/scala/akka/persistence/jdbc/state/ScaladslSnippets.scala) { #changes }

Java
: @@snip[snip](/core/src/test/java/akka/persistence/jdbc/state/JavadslSnippets.java) { #changes }

