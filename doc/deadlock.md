

# Slick Scheduling Algorithm
For the [new scheduling algorithm in #1461](https://github.com/slick/slick/pull/1461) to work correctly without deadlocks
it is critical that Slicks knows the connection pool size. It is set automatically [when you let Slick configure HikariCP](https://github.com/szeiger/slick/commit/353a6e41f389fbe776f1c38166bbe1a3f0a3f2e0)
by calling `Database.forDatabase(...)` or `Database.forDataSource` where the default maxConnections is set to `1` connection if you don't override it yourself which
for most cases if too low, in all other cases you have to do it on your own by using

Introduction of priority levels:

- __HighPriority__: a DBIO which already has a connection associated (due to running in a transaction or with pinned session)
- __MediumPriority__: the old highPrio = true case: a continuation of another DBIO action, it should always be able to be enqueued
- __LowPriority__: any other DBIO, if the queue is full it will not be enqueued

The queue backing Slick's ThreadPoolExecutor now consists internally of two backing queues:

- A __HighPriority__ queue which contains only the HighPriority runnables
- The old queue containing the other priority levels.

AsyncExecutor

HighPriority items are always taken first, until this queue is exhausted and only then Low- or MediumPriority items are considered.

We also do connection counting in the HikariCPJdbcDataSource:
When there are no more connections in the pool, we prevent low/medium priority items from being processed from the queue.
Only HighPriority items are able to make progress, because they already have a connection.

## Database thread pool
Every Database contains an AsyncExecutor that manages the thread pool for asynchronous execution of Database I/O Actions.
Its size is the main parameter to tune for the best performance of the Database object. It should be set to the value that
you would use for the size of the connection pool in a traditional, blocking application.

When using Database.forConfig, the thread pool is configured directly in the external configuration file together with
the connection parameters. If you use any other factory method to get a Database, you can either use a default configuration
or specify a custom AsyncExecutor:

```scala
val db = Database.forURL("jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1", driver="org.h2.Driver",
  executor = AsyncExecutor("test1", numThreads=10, queueSize=1000))
```

## Configuration
The object `akka.persistence.jdbc.util.SlickDatabase` reads the HikariJdbcDataSource configuration from typesafe
configuration from the object `slick.db`. It expects a `connectionPool` field that has been set to `HikariCP`
The class `slick.jdbc.hikaricp.HikariCPJdbcDataSource` reads the configured Hikari Connection Pool configuration.

```scala
def forConfig(c: Config, driver: Driver, name: String, classLoader: ClassLoader): HikariCPJdbcDataSource = {
    val hconf = new HikariConfig()

    // Connection settings
    if (c.hasPath("dataSourceClass")) {
      hconf.setDataSourceClassName(c.getString("dataSourceClass"))
    } else {
      Option(c.getStringOr("driverClassName", c.getStringOr("driver"))).map(hconf.setDriverClassName _)
    }
    hconf.setJdbcUrl(c.getStringOr("url", null))
    c.getStringOpt("user").foreach(hconf.setUsername)
    c.getStringOpt("password").foreach(hconf.setPassword)
    c.getPropertiesOpt("properties").foreach(hconf.setDataSourceProperties)

    // Pool configuration
    hconf.setConnectionTimeout(c.getMillisecondsOr("connectionTimeout", 1000))
    hconf.setValidationTimeout(c.getMillisecondsOr("validationTimeout", 1000))
    hconf.setIdleTimeout(c.getMillisecondsOr("idleTimeout", 600000))
    hconf.setMaxLifetime(c.getMillisecondsOr("maxLifetime", 1800000))
    hconf.setLeakDetectionThreshold(c.getMillisecondsOr("leakDetectionThreshold", 0))
    hconf.setInitializationFailFast(c.getBooleanOr("initializationFailFast", false))
    c.getStringOpt("connectionTestQuery").foreach(hconf.setConnectionTestQuery)
    c.getStringOpt("connectionInitSql").foreach(hconf.setConnectionInitSql)
    val numThreads = c.getIntOr("numThreads", 20)
    hconf.setMaximumPoolSize(c.getIntOr("maxConnections", numThreads * 5))
    hconf.setMinimumIdle(c.getIntOr("minConnections", numThreads))
    hconf.setPoolName(c.getStringOr("poolName", name))
    hconf.setRegisterMbeans(c.getBooleanOr("registerMbeans", false))

    // Equivalent of ConnectionPreparer
    hconf.setReadOnly(c.getBooleanOr("readOnly", false))
    c.getStringOpt("isolation").map("TRANSACTION_" + _).foreach(hconf.setTransactionIsolation)
    hconf.setCatalog(c.getStringOr("catalog", null))

    val ds = new HikariDataSource(hconf)
    new HikariCPJdbcDataSource(ds, hconf)
  }
```

## Resources
- [Transactionally deadlock still there on 3.2.0-M1?](https://github.com/slick/slick/issues/1614)
- [Slick deadlock #1461](https://github.com/slick/slick/pull/1461)
- [connection leak detected #1678](https://github.com/slick/slick/issues/1678)
