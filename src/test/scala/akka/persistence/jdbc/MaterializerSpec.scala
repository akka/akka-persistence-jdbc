package akka.persistence.jdbc

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.ConfigFactory

trait MaterializerSpec {
  val config = ConfigFactory.load()
  implicit val system: ActorSystem = ActorSystem("test", config)
  implicit val mat: Materializer = ActorMaterializer()
}
