/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state.scaladsl

import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.{ ExecutionContext, Future }

trait DataGenerationHelper extends ScalaFutures {

  implicit def defaultPatience: PatienceConfig

  // upsert multiple records for 1 persistence id
  def upsertManyForOnePersistenceId(
      store: JdbcDurableStateStore[String],
      persistenceId: String,
      tag: String,
      startIndex: Int,
      n: Int) = {
    (startIndex until startIndex + n).map { c =>
      store.upsertObject(persistenceId, c, s"$c valid string", tag).futureValue
    }
  }

  // upsert multiple records for 1 persistence id
  def upsertForManyDifferentPersistenceIds(
      store: JdbcDurableStateStore[String],
      persistenceIdPrefix: String,
      revision: Int,
      tag: String,
      startIndex: Int,
      n: Int) = {
    (startIndex until startIndex + n).map { c =>
      store.upsertObject(s"$persistenceIdPrefix-$c", revision, s"$c valid string", tag).futureValue
    }
  }

  private def times(n: Int, ls: List[String]) = ls.flatMap { List.fill(n)(_) }

  // upsert multiple records for a random shuffle of a list of persistence ids
  def upsertRandomlyShuffledPersistenceIds(
      store: JdbcDurableStateStore[String],
      persistenceIds: List[String],
      tag: String,
      replicationFactor: Int) = {
    val allPersistenceIds = scala.util.Random.shuffle(times(replicationFactor, persistenceIds))
    val m = collection.mutable.Map.empty[String, Long]
    allPersistenceIds.map { p =>
      m.get(p)
        .fold {
          val _ = store.upsertObject(p, 1, s"1 valid string", tag).futureValue
          m += ((p, 1))
        } { seq =>
          {
            val _ = store.upsertObject(p, seq + 1, s"${seq + 1} valid string", tag).futureValue
            m += ((p, seq + 1))
          }
        }
    }
  }

  def upsertParallel(store: JdbcDurableStateStore[String], pids: Set[String], tag: String, noOfItems: Int)(
      implicit ec: ExecutionContext) = {

    for {
      _ <- Future.unit
      f1 = Future(upsertManyForOnePersistenceId(store, pids.head, tag, 1, noOfItems))
      f2 = Future(upsertManyForOnePersistenceId(store, pids.tail.head, tag, 1, noOfItems))
      f3 = Future(upsertManyForOnePersistenceId(store, pids.last, tag, 1, noOfItems))
      _ <- f1
      _ <- f2
      _ <- f3
    } yield (())
  }

  def upsertParallelMany(store: JdbcDurableStateStore[String], pids: Set[String], tag: String, noOfItems: Int)(
      implicit ec: ExecutionContext) = {

    for {
      _ <- Future.unit
      f1 = Future(upsertManyForOnePersistenceId(store, pids.head, tag, 1, noOfItems))
      f2 = Future(upsertManyForOnePersistenceId(store, pids.tail.head, tag, 1, noOfItems))
      f3 = Future(upsertManyForOnePersistenceId(store, pids.tail.tail.head, tag, 1, noOfItems))
      f4 = Future(upsertManyForOnePersistenceId(store, pids.tail.tail.tail.head, tag, 1, noOfItems))
      f5 = Future(upsertManyForOnePersistenceId(store, pids.tail.tail.tail.tail.head, tag, 1, noOfItems))
      f6 = Future(upsertManyForOnePersistenceId(store, pids.tail.tail.tail.tail.tail.head, tag, 1, noOfItems))
      f7 = Future(upsertManyForOnePersistenceId(store, pids.last, tag, 1, noOfItems))
      _ <- f1
      _ <- f2
      _ <- f3
      _ <- f4
      _ <- f5
      _ <- f6
      _ <- f7
    } yield (())
  }
}
