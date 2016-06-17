package akka.persistence.jdbc.util

import scala.util.{Failure, Success, Try}


object TrySeq {
  def sequence[R](seq : Seq[Try[R]]): Try[Seq[R]] = {
    seq match {
      case Success(h) :: tail =>
        tail.foldLeft(Try(h :: Nil)) {
          case (Success(acc), Success(elem)) => Success(elem :: acc)
          case (e : Failure[_], _) => e
          case (_, Failure(e)) => Failure(e)
        }
      case Failure(e) :: _  => Failure(e)
      case Nil => Try { Nil }
    }
  }
}
