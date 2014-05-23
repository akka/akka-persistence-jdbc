package akka.persistence.jdbc.common

trait Config {
  def username: String
  def password: String
  def driverClassName: String
  def url: String
  def maxActive: Int
  def maxIdle: Int
  def initialSize: Int
}
