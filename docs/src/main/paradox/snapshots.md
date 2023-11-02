---
project.description: Snapshot builds via the Sonatype snapshot repository.
---
# Snapshots

Snapshots are published to https://repo.akka.io/snapshots repository after every successful build on master.
Add the following to your project build definition to resolve Akka Persistence JDBC's snapshots:

## Configure repository

Maven
:   ```xml
    <project>
    ...
        <repositories>
          <repositories>
            <repository>
              <id>akka-repository</id>
              <name>Akka library snapshot repository</name>
              <url>https://repo.akka.io/snapshots</url>
            </repository>
          </repositories>
        </repositories>
    ...
    </project>
    ```

sbt
:   ```scala
    resolvers += "Akka library snapshot repository".at("https://repo.akka.io/snapshots")
    ```

Gradle
:   ```gradle
    repositories {
      maven {
        url  "https://repo.akka.io/snapshots"
      }
    }
    ```

## Documentation

The [snapshot documentation](https://doc.akka.io/docs/akka-persistence-jdbc/snapshot) is updated with every snapshot build.

