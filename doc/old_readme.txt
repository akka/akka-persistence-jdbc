## Oracle configuration
Base your akka-persistence-jdbc `application.conf` on [this config file][oracle-application.conf]

## Oracle schema
The schema is available [here][oracle-schema]

Configure `slick`:
- The following slick drivers are supported:
  - `slick.driver.PostgresDriver$`
  - `slick.driver.MySQLDriver$`
  - `slick.driver.H2Driver$`
  - `com.typesafe.slick.driver.oracle.OracleDriver$`


## Slick Extensions Licensing Changing to Open Source
The [Typesafe/Lightbend Slick Extensions][slick-ex] have become [open source as
of 1 february 2016 as can read from the slick new website][slick-ex-lic],
this means that you can use akka-persistence-jdbc with no commercial license from Typesafe/Lightbend when used with `Oracle`, `IBM DB2` or
`Microsoft SQL Server`. Thanks [Lightbend][lightbend]! Of course you will need a commercial license from
your database vendor.

Alternatively you can opt to use [Postgresql][postgres], which is the most advanced open source database
available, with some great features, and it works great together with akka-persistence-jdbc.
