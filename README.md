Akka
====
*Akka is a powerful platform that simplifies building and operating highly responsive, resilient, and scalable services.*


The platform consists of
* the [**Akka SDK**](https://doc.akka.io/) for straightforward, rapid development with AI assist and automatic clustering. Services built with the Akka SDK are automatically clustered and can be deployed on any infrastructure.
* and [**Akka Automated Operations**](https://doc.akka.io/operations/akka-platform.html), a managed solution that handles everything for Akka SDK services from auto-elasticity to multi-region high availability running safely within your VPC.

The **Akka SDK** and **Akka Automated Operations** are built upon the foundational [**Akka libraries**](https://doc.akka.io/libraries/akka-dependencies/current/), providing the building blocks for distributed systems.


JDBC plugin for Akka Persistence
================================

akka-persistence-jdbc writes journal and snapshot entries to a configured JDBC store. It implements the full akka-persistence-query API and is therefore very useful for implementing DDD-style 
application models using Akka for creating reactive applications.

Please note that the H2 database is not recommended to be used as a production database, and support for H2 is primarily for testing purposes.

The Akka Persistence JDBC was originally created by @dnvriend.

Reference Documentation
-----------------------

The reference documentation for all Akka libraries is available via [doc.akka.io/libraries/](https://doc.akka.io/libraries/), details for the Akka JDBC plugin
for [Scala](https://doc.akka.io/libraries/akka-persistence-jdbc/current/?language=scala) and [Java](https://doc.akka.io/libraries/akka-persistence-jdbc/current/?language=java).

The current versions of all Akka libraries are listed on the [Akka Dependencies](https://doc.akka.io/libraries/akka-dependencies/current/) page. Releases of the Akka JDBC plugin in this repository are listed on the [GitHub releases](https://github.com/akka/akka-persistence-jdbc/releases) page.


## Contributing

Contributions are *very* welcome! The Akka team appreciates community contributions by both those new to Akka and those more experienced.

If you find an issue that you'd like to see fixed, the quickest way to make that happen is to implement the fix and submit a pull request.

Refer to the [CONTRIBUTING.md](CONTRIBUTING.md) file for more details about the workflow, and general hints on how to prepare your pull request.

You can also ask for clarifications or guidance in GitHub issues directly, or in the [akka forum](https://discuss.akka.io/c/akka/).

## License

Akka is licensed under the Business Source License 1.1, please see the [Akka License FAQ](https://www.lightbend.com/akka/license-faq).

Tests and documentation are under a separate license, see the LICENSE file in each documentation and test root directory for details.
