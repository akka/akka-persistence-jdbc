/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state.scaladsl

import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.{ ExecutionContext, Future }

trait DataGenerationHelper extends ScalaFutures {

  // upsert multiple records for 1 persistence id
  def upsertManyFor(
      store: JdbcDurableStateStore[String],
      persistenceId: String,
      tag: String,
      startIndex: Int,
      n: Int) = {
    (startIndex to startIndex + n - 1).map { c =>
      store.upsertObject(persistenceId, c, s"$c valid string", tag).futureValue
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
      f1 = Future(upsertManyFor(store, pids.head, tag, 1, noOfItems))
      f2 = Future(upsertManyFor(store, pids.tail.head, tag, 1, noOfItems))
      f3 = Future(upsertManyFor(store, pids.last, tag, 1, noOfItems))
      _ <- f1
      _ <- f2
      _ <- f3
    } yield (())
  }
}
