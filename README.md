# Akka Persistence JDBC

The Akka family of projects is managed by teams at [Lightbend](https://lightbend.com/) with help from the community.

akka-persistence-jdbc writes journal and snapshot entries to a configured JDBC store. It implements the full akka-persistence-query API and is therefore very useful for implementing DDD-style 
application models using Akka for creating reactive applications.

Please note that the H2 database is not recommended to be used as a production database, and support for H2 is primarily for testing purposes.

## Documentation

* [current Akka Persistence JDBC documentation](https://doc.akka.io/libraries/akka-persistence-jdbc/current/)
* [Akka Persistence JDBC 3.5.x documentation](https://doc.akka.io/libraries/akka-persistence-jdbc/3.5/)
* [Snapshot documentation](https://doc.akka.io/libraries/akka-persistence-jdbc/snapshot/)

## Release notes

The release notes can be found [here](https://github.com/akka/akka-persistence-jdbc/releases).

For the change log prior to v3.2.0, visit [Version History Page (wiki)](https://github.com/akka/akka-persistence-jdbc/wiki/Version-History).

## Community

You can join these groups and chats to discuss and ask Akka related questions:

- Forums: [discuss.akka.io](https://discuss.lightbend.com/c/akka/)
- Issue tracker: [![github: akka/akka-persistence-jdbc](https://img.shields.io/badge/github%3A-issues-blue.svg?style=flat-square)](https://github.com/akka/akka-persistence-jdbc/issues)

In addition to that, you may enjoy following:

- The [Akka Team Blog](https://akka.io/blog/)
- [@akkateam](https://twitter.com/akkateam) on Twitter
- Questions tagged [#akka on StackOverflow](https://stackoverflow.com/questions/tagged/akka)

The Akka Peristence JDBC was originally created by @dnvriend.


## Contributing

Contributions are *very* welcome! The Akka team appreciates community contributions by both those new to Akka and those more experienced.

If you find an issue that you'd like to see fixed, the quickest way to make that happen is to implement the fix and submit a pull request.

Refer to the [CONTRIBUTING.md](CONTRIBUTING.md) file for more details about the workflow, and general hints on how to prepare your pull request.

You can also ask for clarifications or guidance in GitHub issues directly, or in the [akka/dev](https://gitter.im/akka/dev) chat if a more real time communication would be of benefit.

## License

Akka is licensed under the Business Source License 1.1, please see the [Akka License FAQ](https://www.lightbend.com/akka/license-faq).

Tests and documentation are under a separate license, see the LICENSE file in each documentation and test root directory for details.
