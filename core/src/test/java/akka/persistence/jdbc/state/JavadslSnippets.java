package akka.persistence.jdbc.state;

import java.util.concurrent.CompletionStage;
import akka.actor.ActorSystem;
import akka.Done;
import akka.NotUsed;
// #create
import akka.persistence.jdbc.testkit.javadsl.SchemaUtils;
// #create
// #jdbc-durable-state-store
import akka.persistence.state.DurableStateStoreRegistry;
import akka.persistence.jdbc.state.javadsl.JdbcDurableStateStore;
// #jdbc-durable-state-store
// #get-object
import akka.persistence.state.DurableStateStoreRegistry;
import akka.persistence.jdbc.state.javadsl.JdbcDurableStateStore;
import akka.persistence.state.javadsl.GetObjectResult;
// #get-object
// #upsert-get-object
import akka.persistence.state.DurableStateStoreRegistry;
import akka.persistence.jdbc.state.javadsl.JdbcDurableStateStore;
import akka.persistence.state.javadsl.GetObjectResult;
// #upsert-get-object
// #delete-object
import akka.persistence.state.DurableStateStoreRegistry;
import akka.persistence.jdbc.state.javadsl.JdbcDurableStateStore;
// #delete-object
// #current-changes
import akka.NotUsed;
import akka.stream.javadsl.Source;
import akka.persistence.state.DurableStateStoreRegistry;
import akka.persistence.jdbc.state.javadsl.JdbcDurableStateStore;
import akka.persistence.query.DurableStateChange;
import akka.persistence.query.NoOffset;
// #current-changes
// #changes
import akka.NotUsed;
import akka.stream.javadsl.Source;
import akka.persistence.state.DurableStateStoreRegistry;
import akka.persistence.jdbc.state.javadsl.JdbcDurableStateStore;
import akka.persistence.query.DurableStateChange;
import akka.persistence.query.NoOffset;
// #changes

final class JavadslSnippets {
  void create() {
    // #create

    ActorSystem system = ActorSystem.create("example");
    CompletionStage<Done> done = SchemaUtils.createIfNotExists(system);
    // #create
  }

  void durableStatePlugin() {
    ActorSystem system = ActorSystem.create("example");

    // #jdbc-durable-state-store

    @SuppressWarnings("unchecked")
    JdbcDurableStateStore<String> store =
        DurableStateStoreRegistry.get(system)
            .getDurableStateStoreFor(JdbcDurableStateStore.class, "akka.persistence.state.jdbc");
    // #jdbc-durable-state-store
  }

  void getObject() {
    ActorSystem system = ActorSystem.create("example");

    // #get-object

    @SuppressWarnings("unchecked")
    JdbcDurableStateStore<String> store =
        DurableStateStoreRegistry.get(system)
            .getDurableStateStoreFor(JdbcDurableStateStore.class, "akka.persistence.state.jdbc");

    CompletionStage<GetObjectResult<String>> futureResult = store.getObject("InvalidPersistenceId");
    try {
      GetObjectResult<String> result = futureResult.toCompletableFuture().get();
      assert !result.value().isPresent();
    } catch (Exception e) {
      // handle exceptions
    }
    // #get-object
  }

  void upsertAndGetObject() {
    ActorSystem system = ActorSystem.create("example");

    // #upsert-get-object

    @SuppressWarnings("unchecked")
    JdbcDurableStateStore<String> store =
        DurableStateStoreRegistry.get(system)
            .getDurableStateStoreFor(JdbcDurableStateStore.class, "akka.persistence.state.jdbc");

    CompletionStage<GetObjectResult<String>> r =
        store
            .upsertObject("p234", 1, "a valid string", "t123")
            .thenCompose(d -> store.getObject("p234"))
            .thenCompose(o -> store.upsertObject("p234", 2, "updated valid string", "t123"))
            .thenCompose(d -> store.getObject("p234"));

    try {
      assert r.toCompletableFuture().get().value().get().equals("updated valid string");
    } catch (Exception e) {
      // handle exceptions
    }
    // #upsert-get-object
  }

  void deleteObject() {
    ActorSystem system = ActorSystem.create("example");

    // #delete-object

    @SuppressWarnings("unchecked")
    JdbcDurableStateStore<String> store =
        DurableStateStoreRegistry.get(system)
            .getDurableStateStoreFor(JdbcDurableStateStore.class, "akka.persistence.state.jdbc");

    CompletionStage<Done> futureResult = store.deleteObject("p123");
    try {
      assert futureResult.toCompletableFuture().get().equals(Done.getInstance());
    } catch (Exception e) {
      // handle exceptions
    }
    // #delete-object
  }

  void currentChanges() {
    ActorSystem system = ActorSystem.create("example");

    // #current-changes

    @SuppressWarnings("unchecked")
    JdbcDurableStateStore<String> store =
        DurableStateStoreRegistry.get(system)
            .getDurableStateStoreFor(JdbcDurableStateStore.class, "akka.persistence.state.jdbc");

    Source<DurableStateChange<String>, NotUsed> willCompleteTheStream =
        store.currentChanges("tag-1", NoOffset.getInstance());
    // #current-changes
  }

  void changes() {
    ActorSystem system = ActorSystem.create("example");

    // #changes

    @SuppressWarnings("unchecked")
    JdbcDurableStateStore<String> store =
        DurableStateStoreRegistry.get(system)
            .getDurableStateStoreFor(JdbcDurableStateStore.class, "akka.persistence.state.jdbc");

    Source<DurableStateChange<String>, NotUsed> willNotCompleteTheStream =
        store.changes("tag-1", NoOffset.getInstance());
    // #changes
  }
}
