package akka.persistence.h2.common

import akka.persistence.jdbc.common.Config

trait H2Config extends Config {
  def username ="sa"
  def password = ""
  def driverClassName = "org.h2.Driver"
//  def url = "jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
//  def url = "jdbc:h2:~/h2-akka-persistence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
  def url = "jdbc:h2:tcp://localhost:9123/~/test"
  def maxActive = 20
  def maxIdle = 10
  def initialSize = 10
}
