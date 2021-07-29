package akka.persistence.jdbc.state

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.Done
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers

object ScaladslSnippets extends ScalaFutures with Matchers {

  def create(): Unit = {
    // #create
    import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils

    implicit val system: ActorSystem = ActorSystem("example")
    val _: Future[Done] = SchemaUtils.createIfNotExists()
    // #create
  }

  def durableStatePlugin(): Unit = {
    implicit val system: ActorSystem = ActorSystem()

    // #jdbc-durable-state-store
    import akka.persistence.state.DurableStateStoreRegistry
    import akka.persistence.jdbc.state.scaladsl.JdbcDurableStateStore
    val store = DurableStateStoreRegistry
      .get(system)
      .durableStateStoreFor[JdbcDurableStateStore[String]]("akka.persistence.state.jdbc")
    // #jdbc-durable-state-store
  }

  def getObject(): Unit = {
    implicit val system: ActorSystem = ActorSystem()

    // #get-object
    import akka.persistence.state.DurableStateStoreRegistry
    import akka.persistence.jdbc.state.scaladsl.JdbcDurableStateStore
    import akka.persistence.state.scaladsl.GetObjectResult

    val store = DurableStateStoreRegistry
      .get(system)
      .durableStateStoreFor[JdbcDurableStateStore[String]]("akka.persistence.state.jdbc")

    val futureResult: Future[GetObjectResult[String]] = store.getObject("InvalidPersistenceId")
    futureResult.futureValue.value shouldBe None
    // #get-object
  }

  def upsertAndGetObject(): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val e = system.dispatcher

    // #upsert-get-object
    import akka.persistence.state.DurableStateStoreRegistry
    import akka.persistence.jdbc.state.scaladsl.JdbcDurableStateStore
    import akka.persistence.state.scaladsl.GetObjectResult

    val store = DurableStateStoreRegistry
      .get(system)
      .durableStateStoreFor[JdbcDurableStateStore[String]]("akka.persistence.state.jdbc")

    val v: Future[GetObjectResult[String]] =
      for {
        n <- store.upsertObject("p234", 1, "a valid string", "t123")
        _ = n shouldBe akka.Done
        g <- store.getObject("p234")
        _ = g.value shouldBe Some("a valid string")
        u <- store.upsertObject("p234", 2, "updated valid string", "t123")
        _ = u shouldBe akka.Done
        h <- store.getObject("p234")
      } yield h

    v.futureValue.value shouldBe Some("updated valid string")
    // #upsert-get-object
  }

  def deleteObject(): Unit = {
    implicit val system: ActorSystem = ActorSystem()

    // #delete-object
    import akka.persistence.state.DurableStateStoreRegistry
    import akka.persistence.jdbc.state.scaladsl.JdbcDurableStateStore

    val store = DurableStateStoreRegistry
      .get(system)
      .durableStateStoreFor[JdbcDurableStateStore[String]]("akka.persistence.state.jdbc")

    store.deleteObject("p123").futureValue shouldBe Done
    store.getObject("p123").futureValue.value shouldBe None
    // #delete-object
  }

  def currentChanges(): Unit = {
    implicit val system: ActorSystem = ActorSystem()

    // #current-changes
    import akka.NotUsed
    import akka.stream.scaladsl.Source
    import akka.persistence.state.DurableStateStoreRegistry
    import akka.persistence.jdbc.state.scaladsl.JdbcDurableStateStore
    import akka.persistence.query.{ DurableStateChange, NoOffset }

    val store = DurableStateStoreRegistry
      .get(system)
      .durableStateStoreFor[JdbcDurableStateStore[String]]("akka.persistence.state.jdbc")

    val willCompleteTheStream: Source[DurableStateChange[String], NotUsed] =
      store.currentChanges("tag-1", NoOffset)
    // #current-changes
  }

  def changes(): Unit = {
    implicit val system: ActorSystem = ActorSystem()

    // #changes
    import akka.NotUsed
    import akka.stream.scaladsl.Source
    import akka.persistence.state.DurableStateStoreRegistry
    import akka.persistence.jdbc.state.scaladsl.JdbcDurableStateStore
    import akka.persistence.query.{ DurableStateChange, NoOffset }

    val store = DurableStateStoreRegistry
      .get(system)
      .durableStateStoreFor[JdbcDurableStateStore[String]]("akka.persistence.state.jdbc")

    val willNotCompleteTheStream: Source[DurableStateChange[String], NotUsed] =
      store.changes("tag-1", NoOffset)
    // #changes
  }
}
