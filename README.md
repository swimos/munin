# munin

A _stateful_, _distributable_, _real-time_ (aside from some mandated polling),
_highly observable_ data-streaming web service that _continuously_ analyzes
r/WhatsThisBird activity. `munin`'s primary responsibility within the overall
`FileTheseBirds` application is to generate community-vouched deductions to be
persisted to a database, but it also doubles as a [Reddit bot that leaves a
comment trail](https://reddit.com/user/FileTheseBirdsBot/comments) of its
internal state.

`munin`'s runtime is a [Swim server](https://github.com/swimos/swim), and we
rely entirely on the Swim platform to achieve the italicized properties. The
source code and documentation here is mostly driver- and "business
logic"-focused; refer to Swim's guides if you wish to learn more about the
runtime.

## Prerequisites

1. JDK 11+
2. A Reddit account with access to a
[script-type application](https://github.com/reddit-archive/reddit/wiki/OAuth2-Quick-Start-Example#first-steps)
3. A `src/main/resources/reddit-config.properties` file that looks like:
    ```text
    clientId=p-jcoLKBynTLew
    clientSecret=gko_LXELoV07ZBNUXrvWZfzE3aI
    redditUser=reddit_bot
    redditPass=snoo
    userAgent=FileTheseBirds/0.1.0 by reddit_bot
    ```
    All credentials above are fake, copied from the linked tutorial. 
4. A 2-column `src/main/resources/ebird-taxa.csv` file, where each row's first
column indicates an eBird taxonomy code, and the second column is the desired
common name for that code. If we use US-locale English common names, then the
first several rows will look like:
    ```
    ostric2,Common Ostrich
    ostric3,Somali Ostrich
    y00934,Common/Somali Ostrich
    grerhe1,Greater Rhea
    lesrhe2,Lesser Rhea
    ```
5. A `src/main/resources/reviewers.txt` file containing the _lowercase_
usernames of reviewer-privileged Reddit accounts
6. A `src/main/resources/nonparticipants.txt` file containing the _lowercase_
usernames of accounts whose posts/comments should never be analyzed

TODO:
- Offer alternative config options to resource files
- We may eventually offer a `readonly` mode that hits `www.reddit.com` instead of
`oauth.reddit.com`. Such a mode doesn't require prerequisites 2 and 3 but has
two restrictions:
    - No ability to post Reddit comments
    - Far stricter rate limitations.

## Build Instructions

[Gradle](https://gradle.org/) 7+ is required to build `munin` from source. You
may  use the provided wrapper scripts to circumvent manually installing Gradle
on your machine, e.g.
```text
./gradlew build
```
on Unix-like systems, and
```text
.\gradlew.bat build
```
on default Windows shells.

## Run Instructions

Gradle provides a developer-convenient `run` command, but running projects as
Gradle tasks incurs noticeable overhead. The "right" way to run `munin` is to

1. Run one of the aforementioned `build` commands
2. Unpack your distributable of choice from `build/distributions`, (either the
tarball or the .zip)
3. Run the appropriate script for your device from `$UNPACK-DIR/bin`.

TODO: run as (journaled) service; dockerize

### Configurations

TODO as this project matures
