Release Akka Persistence JDBC $VERSION$

<!--
# Release Train Issue Template for Akka Persistence JDBC

(Liberally copied and adopted from Scala itself https://github.com/scala/scala-dev/blob/b11cd2e4a4431de7867db6b39362bea8fa6650e7/notes/releases/template.md)

For every release, make a copy of this file named after the release, and expand the variables.
Ideally replacing variables could become a script you can run on your local machine.

Variables to be expanded in this template:
- $VERSION$=???

Key links:
  - akka/akka-persistence-jdbc milestone: https://github.com/akka/akka-peristence-jdbc/milestone/?
-->
### ~ 1 week before the release

- [ ] Check that open PRs and issues assigned to the milestone are reasonable
- [ ] Create a new milestone for the [next version](https://github.com/akka/akka-persistence-jdbc/milestones)
- [ ] Check [closed issues without a milestone](https://github.com/akka/akka-persistence-jdbc/issues?utf8=%E2%9C%93&q=is%3Aissue%20is%3Aclosed%20no%3Amilestone) and either assign them the 'upcoming' release milestone or `invalid/not release-bound`
- [ ] Close the [$VERSION$ milestone](https://github.com/akka/akka-persistence-jdbc/milestones?direction=asc&sort=due_date)

### 1 day before the release

- [ ] Make sure all important / big PRs have been merged by now
- [ ] Communicate that a new version is about to be released in [Gitter Akka Dev Channel](https://gitter.im/akka/dev)

### Preparing release notes in the documentation / announcement

- [ ] For non-patch releases: rename the 'akka-persistence-jdbc-x.x-stable' reporting projects in [WhiteSource](https://saas.whitesourcesoftware.com/Wss/WSS.html#!project;id=1706072) accordingly
- [ ] Review the [draft release notes](https://github.com/akka/akka-persistence-jdbc/releases)
- [ ] For non-patch releases: Create a news item draft PR on [akka.github.com](https://github.com/akka/akka.github.com), using the milestone

### Cutting the release

- [ ] Wait until [master build finished](https://travis-ci.com/akka/akka-persistence-jdbc/builds/) after merging the latest PR
- [ ] Update the [draft release](https://github.com/akka/akka-persistence-jdbc/releases) with the next tag version `v$VERSION$`, title and release description linking to announcement and milestone
- [ ] Check that GitHub Actions release build has executed successfully (GitHub Actions will start a [CI build](https://github.com/akka/akka-persistence-jdbc/actions) for the new tag and publish artifacts to Sonatype)

### Check availability

- [ ] Check [API](https://doc.akka.io/api/akka-persistence-jdbc/$VERSION$/) documentation
- [ ] Check [reference](https://doc.akka.io/docs/akka-persistence-jdbc/$VERSION$/) documentation. Check that the reference docs were deployed and show a version warning (see section below on how to fix the version warnning).
- [ ] Check the release on [Maven central](https://repo1.maven.org/maven2/com/lightbend/akka/akka-persistence-jdbc_2.12/$VERSION$/)

### When everything is on maven central
  - [ ] Log into `gustav.akka.io` as `akkarepo` 
    - [ ] If this updates the `current` version, run `./update-akka-persistence-jdbc.sh $VERSION$`
    - [ ] otherwise check changes and commit the new version to the local git repository
         ```
         cd ~/www
         git status
         git add docs/akka-persistence-jdbc/current docs/akka-persistence-jdbc/$VERSION$
         git add api/akka-persistence-jdbc/current api/akka-persistence-jdbc/$VERSION$
         git commit -m "Akka Persistence JDBC $VERSION$"
         ```

### Announcements

- [ ] For non-patch releases: Merge draft news item for [akka.io](https://github.com/akka/akka.github.com)
- [ ] Send a release notification to [Lightbend discuss](https://discuss.akka.io)
- [ ] Tweet using the [@akkateam](https://twitter.com/akkateam/) account (or ask someone to) about the new release
- [ ] Announce on [Gitter akka/akka](https://gitter.im/akka/akka)
- [ ] Announce internally (with links to Tweet, blog, discuss)

### Afterwards

- [ ] Update version for [Lightbend Supported Modules](https://developer.lightbend.com/docs/lightbend-platform/introduction/getting-help/build-dependencies.html) in [private project](https://github.com/lightbend/lightbend-technology-intro-doc/blob/master/docs/modules/getting-help/examples/build.sbt)
- Close this issue
