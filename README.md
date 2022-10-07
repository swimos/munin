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

## Available WARP APIs

`munin` exposes several streaming endpoints via its Web Agents. Below, we list
the ones that you are most likely to find useful. `munin` delivers only the last
36 hours worth of r/WhatsThisBird activity in all of these APIs, but will be
processing far more than that under the hood.

We use `swim-cli` commands in the following examples for simplicity; these may
just as easily be translated into downlinks.

### Statuses of all posts

```
% swim-cli sync -h warp://localhost:9001 -n /submissions -l statuses

@update(key:"/submission/xwgwh6")@status{id:xwgwh6,title:"What species are these? In eastern washingon and I saw one jump",location:"north america",thumbnail:"https://b.thumbs.redditmedia.com/svwmIdeX0vhhgUT_sYSXc1SJ-jMizyMUJEr7hg9HFGE.jpg",createdUtc:1664991193,karma:8,commentCount:5,taxa:{bkbmag1},reviewers:{brohitbrose}}
@update(key:"/submission/xwgwj0")@status{id:xwgwj0,title:"Anyone know what this feather could belong to? West Michigan, USA.",location:"north america",thumbnail:"https://a.thumbs.redditmedia.com/_gmhGQJpMM7R04UigBVFsVkXY7h7fh0dPhT_CtnvHo8.jpg",createdUtc:1664991196,karma:14,commentCount:5,taxa:{wiltur},reviewers:{tinylongwing}}
@update(key:"/submission/xwgz1y")@status{id:xwgz1y,title:"Bird found in Tom Green County",location:"north america",thumbnail:"https://a.thumbs.redditmedia.com/C8f_G4ZEs2pDdWQRA8MmKOjnTb24kYRUi5M6E2vJ2w8.jpg",createdUtc:1664991356,karma:8,commentCount:3,taxa:{sora},reviewers:}
...
```

### Unanswered(/answered/unreviewed/reviewed) submissions

```
% swim-cli sync -h warp://localhost:9001 -n /submissions -l unanswered

@update(key:xwaj1m)@status{id:xwaj1m,title:"Can anyone ID this strange bird sound?",location:unknown,thumbnail:"https://b.thumbs.redditmedia.com/xhMyBWKfdQzhWKITLRHHYlLF_wQAqHkFGPBtHwSVvjo.jpg",createdUtc:1664976070,karma:7,commentCount:3,taxa:,reviewers:}
@update(key:xwxh4k)@status{id:xwxh4k,title:"Not a usual bird we see in the city. Location: Philippines",location:"southeast asia",thumbnail:"https://b.thumbs.redditmedia.com/zpt1ueqL9S1N6Pb-e2x0ImGNUrh8f-Z5wzVp-mb2z0A.jpg",createdUtc:1665035125,karma:13,commentCount:11,taxa:,reviewers:}
@update(key:xx19q7)@status{id:xx19q7,title:"ID request: Eastern Alps in Switzerland, dropped out of the sky",location:europe,thumbnail:"https://b.thumbs.redditmedia.com/LG8Rr7xkdLEiFg5FwGTNtU2hV2OQ5IRbZcH6_IlKlcw.jpg",createdUtc:1665049854,karma:15,commentCount:0,taxa:,reviewers:}
...
```

The `answered`, `unreviewed`, and `reviewed` lanes are similarly available.

### No-fluff answers for all submissions

```
% swim-cli sync -h warp://localhost:9001 -n /throttledPublish -l answers 

@update(key:"/submission/xxkv53")@answer{taxa:{comyel}}
@update(key:"/submission/xxkwcq")
@update(key:"/submission/xxldl3")@answer{taxa:{grbher,bcnher,greegr}}
@update(key:"/submission/xxlwlz")@answer{taxa:{babwar},reviewers:{broken_faaace}}
...
```
