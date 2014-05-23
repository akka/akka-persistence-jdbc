package akka.persistence.postgresql.common

import akka.persistence.jdbc.common.Config

trait PostgresqlConfig extends Config {
  def username ="propositionengine"
  def password = "propositionengine"
  def driverClassName = "org.postgresql.Driver"
  def url = "jdbc:postgresql://localhost:5432/propositionengine"
  def maxActive = 20
  def maxIdle = 10
  def initialSize = 10
}
