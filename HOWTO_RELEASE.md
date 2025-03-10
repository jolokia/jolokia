# How to release Jolokia

There are two major steps required to release Jolokia:
* test, build and deploy Jolokia artifacts to Maven Central
* build and publish the website

## Build and test

Building the project is a straightforward step. Jolokia is a Maven project and there's nothing unusual in build configuration, no exotic plugins involved and no antrun tasks.

```console
mvn clean install
```

Maven build will involve invocation of standard unit and integration tests. If you have a firewall enabled, there may
be problems running UDP multicast tests:

```console
[ERROR] Tests run: 9, Failures: 2, Errors: 0, Skipped: 0, Time elapsed: 4.068 s <<< FAILURE! -- in TestSuite
[ERROR] org.jolokia.service.discovery.MulticastSocketListenerThreadTest.simple -- Time elapsed: 1.168 s <<< FAILURE!
java.lang.AssertionError: Exactly one in message with the send id should have been received expected [1] but found [0]
	at org.testng.Assert.fail(Assert.java:110)
	at org.testng.Assert.failNotEquals(Assert.java:1413)
	at org.testng.Assert.assertEqualsImpl(Assert.java:149)
	at org.testng.Assert.assertEquals(Assert.java:131)
	at org.testng.Assert.assertEquals(Assert.java:1240)
	at org.jolokia.service.discovery.MulticastSocketListenerThreadTest.simple(MulticastSocketListenerThreadTest.java:78)
...

[ERROR] org.jolokia.service.discovery.DiscoveryMulticastResponderTest.enabledLookup -- Time elapsed: 1.323 s <<< FAILURE!
java.lang.AssertionError: expected [false] but found [true]
	at org.testng.Assert.fail(Assert.java:110)
	at org.testng.Assert.failNotEquals(Assert.java:1413)
	at org.testng.Assert.assertFalse(Assert.java:78)
	at org.testng.Assert.assertFalse(Assert.java:88)
	at org.jolokia.service.discovery.DiscoveryMulticastResponderTest.lookup(DiscoveryMulticastResponderTest.java:56)
	at org.jolokia.service.discovery.DiscoveryMulticastResponderTest.enabledLookup(DiscoveryMulticastResponderTest.java:25)
...
```

This can be fixed with proper firewall configuration. Here's an example of `nft` command on Fedora with `FedoraServer` default zone:
```console
# firewall-cmd --get-default-zone 
FedoraServer

# nft add rule inet firewalld filter_IN_FedoraServer_allow ip daddr 239.192.48.84 accept
# nft add rule inet firewalld filter_IN_FedoraServer_allow ip6 daddr ff08::48:84 accept

# nft -a list chain inet firewalld filter_IN_FedoraServer_allow
table inet firewalld {
	chain filter_IN_FedoraServer_allow { # handle 153
		...
		ip daddr 239.192.48.84 accept # handle 342
		ip6 daddr ff08::48:84 accept # handle 343
	}
}

(run the tests)

# nft delete rule inet firewalld filter_IN_FedoraServer_allow handle 342
# nft delete rule inet firewalld filter_IN_FedoraServer_allow handle 343
```

There are however additional tests that should be run a bit outside of standard `mvn clean install`.

Instead of complex configuration that would be required for frameworks like [Selenium][1], Javascript tests are run
from the web browser after running examples/client-javascript-test-app application:

```console
$ mvn clean package -DskipTests jetty:run-war -f examples/client-javascript-test-app
[INFO] Scanning for projects...
[INFO] 
[INFO] -------< org.jolokia:jolokia-example-client-javascript-test-app >-------
[INFO] Building jolokia-example-client-javascript-test-app 2.1.0-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ war ]---------------------------------
...
[INFO] Started o.e.j.m.p.MavenWebAppContext@1826475{JSON JMX Agent,/,file:///data/sources/github.com/jolokia/jolokia/examples/client-javascript-test-app/target/jolokia-example-client-javascript-test-app-2.1.0-SNAPSHOT/,AVAILABLE}{/data/sources/github.com/jolokia/jolokia/examples/client-javascript-test-app/target/jolokia-example-client-javascript-test-app-2.1.0-SNAPSHOT.war}
[INFO] Started ServerConnector@79e15c4a{HTTP/1.1, (http/1.1)}{0.0.0.0:8080}
[INFO] Started Server@5f9ccd0c{STARTING}[11.0.22,sto=0] @4430ms
[INFO] Scan interval ms = 10
```

JavaScript tests are run by browsing to one of:
* http://localhost:8080/jolokia-test.html - tests for `jolokia.js`
* http://localhost:8080/jolokia-simple-test.html - tests for `jolokia-simple.js`
* http://localhost:8080/jolokia-poller-test.html - tests for polling part of `jolokia.js`
* http://localhost:8080/jolokia-all-test.html - all tests combined.

JavaScript tests are run with the help of [QUnit][3]. To make the work smoother without a need to rebuild `client/javascript-esm`
and `examples/client-javascript-test-app`, there's one handy `makeLinks.sh` shell script (for Linux) that replaces the target
files with symbolic links to original locations.

Just `cd` into correct directory and run the script:
```console
$ cd examples/client-javascript-test-app/
 
$ ./makeLinks.sh 
+ cd target/
++ find . -type d -name 'jolokia-example-client-javascript-test-app-*'
+ WARUNPACKED=./jolokia-example-client-javascript-test-app-2.1.0-SNAPSHOT
+ '[' -d ./jolokia-example-client-javascript-test-app-2.1.0-SNAPSHOT ']'
+ cd ./jolokia-example-client-javascript-test-app-2.1.0-SNAPSHOT
+ rm jolokia-all-test.html
+ rm jolokia-chat.html
+ rm jolokia-poller-test.html
+ rm jolokia-simple-test.html
+ rm jolokia-test.html
+ rm demo/plot.html
+ ln -s ../../src/main/webapp/jolokia-all-test.html .
+ ln -s ../../src/main/webapp/jolokia-chat.html .
+ ln -s ../../src/main/webapp/jolokia-poller-test.html .
+ ln -s ../../src/main/webapp/jolokia-simple-test.html .
+ ln -s ../../src/main/webapp/jolokia-test.html .
+ ln -s ../../src/main/webapp/demo/plot.html demo
+ cd scripts/lib
+ rm jolokia.js
+ rm jolokia-simple.js
++ jq -r .version ../../../../../../client/javascript-esm/packages/jolokia/package.json
+ JS_VERSION=2.1.5
+ ln -s ../../../../../../client/javascript-esm/packages/jolokia/dist/jolokia.js jolokia.js
+ ln -s ../../../../../../client/javascript-esm/packages/jolokia-simple/dist/jolokia-simple.js jolokia-simple.js
+ cd ../test
+ rm jolokia-poller-test.js
+ rm jolokia-simple-test.js
+ rm jolokia-test.js
+ ln -s ../../../../src/main/webapp/scripts/test/jolokia-poller-test.js .
+ ln -s ../../../../src/main/webapp/scripts/test/jolokia-simple-test.js .
+ ln -s ../../../../src/main/webapp/scripts/test/jolokia-test.js .
```

When Jolokia only used single location for JavaScript libraries developed _directly_ (`client/javascript/src/main/javascript/jolokia.js`), the above script and symbolic links were enough to make the work smoother.

New Jolokia JavaScript library is based on TypeScript and bundled into various targets using [Rollup.js][8] so we also need to start _watched build_. It is single additional step:

```shell
$ cd client/javascript-esm/
 
$ yarn watch
[jolokia.js]: Process started
[@jolokia.js/simple]: Process started
...
[jolokia.js]: rollup v4.22.4
[jolokia.js]: bundles src/jolokia.ts → dist/jolokia.cjs, dist/jolokia.min.cjs, dist/jolokia.mjs...
[@jolokia.js/simple]: rollup v4.22.4
[@jolokia.js/simple]: bundles src/jolokia-simple.ts → dist/jolokia-simple.cjs, dist/jolokia-simple.min.cjs, dist/jolokia-simple.mjs...
[@jolokia.js/simple]: (!) [plugin typescript] @rollup/plugin-typescript: Rollup 'sourcemap' option must be set to generate source maps.
[@jolokia.js/simple]: created dist/jolokia-simple.cjs, dist/jolokia-simple.min.cjs, dist/jolokia-simple.mjs in 1.1s
[jolokia.js]: (!) [plugin typescript] @rollup/plugin-typescript: Rollup 'sourcemap' option must be set to generate source maps.
[jolokia.js]: created dist/jolokia.cjs, dist/jolokia.min.cjs, dist/jolokia.mjs in 1.2s
```

With the symbolic links created, just change the test files or `jolokia.ts` in your IDE of choice and re-run the tests (by refreshing browser page or using QUnit UI).

## Manage version numbers

There's an unresolved [MRELEASE-798][2] issue for maven-release-plugin that describes a scenario which would be very
useful in Jolokia project.
`mvn release:prepare` + `mvn release:perform` is the standard deployment procedure for Maven projects, but it only
automatically updates the versions in POM files, creating well known pairs of commits like:

```
* (2022-12-26 10:23:51 +0100) fffaa40d [maven-release-plugin] prepare for next development iteration <Roland Huß>
* (2022-12-26 10:23:47 +0100) 42dec09d [maven-release-plugin] prepare release v1.7.2 <Roland Huß> (tag: v1.7.2)
```

The problem is that Jolokia also includes JavaScript files (and descriptors like `package.json`) which contain
version numbers.

There are well established practices used when a released version is used in Java applications - usually through `*.properties` files and Maven filtering. However when Javascript projects are intermixed with Java it's getting more difficult.

Jolokia 2 has exactly **four** versions stored in various places. However in some places the version is managed automatically (thanks to maven-release-plugin and Maven filtering).

* _Maven version_ - managed automatically by maven-release-plugin with `-SNAPSHOT` qualifier in-between releases. This version may be used in Java code with `*.properties` files stored in `src/main/resources` with filtering enabled. This version is available in filtered resources when using `${project.version}` placeholder.
* _Current stable version_ - this is the recently released version defined in main `pom.xml` as `<properties>/<currentStableVersion>` value. This version is needed, because documentation should not refer to `${project.version}` directly (which is `-SNAPSHOT` qualified most of the time). And usually documentation is being updated for some time after the release.
* _Jolokia protocol version_ - this is the version of protocol and is unrelated to Java/Maven version. Should be managed manually in main `pom.xml` using `<protocolVersion>` Maven property.
* _JavaScript package/client version_ - this is the `version` field of `package.json` for Jolokia JavaScript client. It uses different convention than Maven/Java version (no `-SNAPSHOT` qualifier for example).

### Current stable version change

After successful release, main `pom.xml` should have `<currentStableVersion>` property updated to match the
latest Maven version set by `maven-release-plugin`. Then we can continue work on documentation. There's no need to
set this version anywhere else, because it's propagated through Maven properties to maven plugins that generate the
site (like maven-site-plugin or `npx` invocation through `frontend-maven-plugin`).

However, during the deployment, a Maven Assembly is created that contains:
* released jars
* source code
* reference manual

Reference manual is generated using Antora and version number is passed as asciidoctor attribute. That's why the
`<currentStableVersion>` SHOULD be set just before the release to a value specified later as `-DreleaseVersion`
parameter for `mvn release:prepare` invocation.

### Maven version change

This is done automatically by using `maven-release-plugin`. No manual work should be required.

### Jolokia protocol version change

This is done during normal development and such change is never part of the release process itself. Protocol version
change should be related to particular [GitHub issue][4].

### JavaScript package/client version change

This can be done just before the release (recommended), but if there are only JavaScript code changes and we want to
deploy new Jolokia JavaScript client package to [NPM Registry][5], it's enough to:

* run `yarn version <new version>` command (see [yarn version](https://yarnpkg.com/cli/version) and [yarn release workflow](https://yarnpkg.com/features/release-workflow)) in all workspaces
* commit and push the changes
* continue with NPM/Yarn release procedure

Before using workspaces, `npm version` also run `package.json` script under `"versions"` key. We used a script that updated one JavaScript file. I can't do it (yet) with `yarn` though...

That's why we have to change `CLIENT_VERSION` field in `client/javascript-esm/packages/jolokia/src/jolokia.ts` manually.

## Update the changelog

Before the release it's worth updating the changelog in `src/changes/changes.xml`, but it can also be done after
the release, because this XML file is processed during `mvn site` generation.

It's also worth adding a release note to `src/site/asciidoc/news.adoc`.

## Deploy and release

The important part of the release is to sign the artifacts with your GPG key. GPG keys can be checked using `gpg -k`
command and the output should contains something like:

```console
$ gpg -k --keyid-format long
/home/ggrzybek/.gnupg/pubring.kbx
---------------------------------
...
pub   rsa4096/DB593BFCBBDC099E 2020-09-02 [SC]
      4C8BD038EE847AB3A6A586EEDB593BFCBBDC099E
...
```

With `--keyid-format long`, key ID is shown after the algorithm. Here it's `DB593BFCBBDC099E`. This value should be set/used
in `-Dgpg.keyname` parameter for `maven-gpg-plugin`. Adding `-Dgpg.useagent=true` will open OS specific dialog to
provide key password (it is `true` by default).

### Build release and deploy it to OSSRH

Jolokia uses Sonatype's Open Source Software Repository Hosting (OSSRH) service to deploy artifacts to Maven Central.
Please check instructions and more information [here][6].
If `developmentVersion`, `releaseVersion` or `tag` properties are not specified, you'll be prompted to provide required
values in the console.

```console
git clone git@github.com:jolokia/jolokia.git
cd jolokia
mvn -Dmaven.repo.local=/tmp/repo \
    -DdevelopmentVersion=2.2.6-SNAPSHOT \
    -DreleaseVersion=2.2.5 \
    -Dtag=v2.2.5 \
    -Pdist release:prepare
mvn -Dmaven.repo.local=/tmp/repo \
    -Pdist release:perform
```

### Copy assembly to GitHub

* Create a new release ("Draft a release" - Button)
* Upload `tar.gz` and `zip` files from `target/checkout/assembly/target`
* Upload JVM debian package from
    * `agent/jvm/target/*.deb`

### Deploy to Sonatype staging

(_should be done automatically with `mvn release:perform`_)

```console
cd target/checkout
mvn -Dmaven.repo.local=/tmp/repo -DskipTests -Pdist deploy
cd ../..
```

### Release on central maven repo

* See: <https://central.sonatype.org/publish/publish-guide/>
* Staging Nexus: <https://oss.sonatype.org/>
* Steps:
    1. Login into <https://oss.sonatype.org/>
    2. "Staging Repositories"
    3. Select staging repository
    4. Click "Close" in menu
    5. Check the artifacts in the "closed" repository
    6. If ok --> click "Release" in menu

### Release JavaScript client NPM package

Before performing the following commands, make sure you've logged in to the [NPM registry](https://www.npmjs.com/) with an account that has publish privilege to:

* [jolokia.js](https://www.npmjs.com/package/jolokia.js) NPM package
* [@jolokia.js/simple](https://www.npmjs.com/package/@jolokia.js/simple) NPM package

Jolokia JavaScript libraries are stored as [yarn workspaces](https://yarnpkg.com/features/workspaces) and use [cross-references](https://yarnpkg.com/features/workspaces#cross-references) with `workspace:^` syntax. Publishing via `npm publish` is not enough - we need `package.json` to contain actual cross-project references, so `yarn npm publish` is required.

```console
cd client/javascript-esm
yarn npm login --publish
yarn install
cd packages/jolokia
yarn npm publish
cd ../jolokia-simple
yarn npm publish
```

## Adapt metadata

### Add new release to `jolokia.meta`

```console
vi src/site/resources/jolokia.meta
```

### Sign and create checksums

```console
gpg --allow-weak-digest-algos --digest-algo=SHA1 --local-user roland@jolokia.org -a -b src/site/resources/jolokia.meta
shasum src/site/resources/jolokia.meta > src/site/resources/jolokia.meta.sha1
md5sum src/site/resources/jolokia.meta > src/site/resources/jolokia.meta.md5
```

## Recreate website (`~/jolokia`)

First, update skin version used in `src/site/site.xml` to new version.

Then just run maven-site-plugin. In Jolokia, we use Asciidoc and [frontend-maven-plugin][7] for npm/Javascript stuff. All
plugins are configured in relevant Maven lifecycle phases, so it's enough to run:

```console
mvn clean site -N -Pdist
```

While we could use `com.github.github:site-maven-plugin`, I've simply copied the content of `target/site` to `gh-pages`
branch of Jolokia and pushed the changes:

```console
git checkout gh-pages
# copy changed site
grep -lr 2.2.6-SNAPSHOT | xargs sed -i 's/2.2.6-SNAPSHOT/2.2.5/g'
git add .
git commit -m '[site] Generate new Jolokia site'
git push origin HEAD
```

## TL;DR - checklist

1. `mvn clean install`
2. `mvn clean package -DskipTests jetty:run-war -f examples/client-javascript-test-app`, check http://localhost:8080/jolokia-all-test.html
3. Set `<currentStableVersion>` to new released version in `pom.xml`
4. If NPM packages are released, Set new JS version in `package.json` files and in `client/javascript-esm/packages/jolokia/src/jolokia.ts` for `CLIENT_VERSION` field
5. Update `src/changes/changes.xml` and `src/site/asciidoc/news.adoc`
6. `mvn -Dmaven.repo.local=/tmp/repo -DdevelopmentVersion=2.2.6-SNAPSHOT -DreleaseVersion=2.2.5 -Dtag=v2.2.5 -Pdist release:prepare`
7. `mvn -Dmaven.repo.local=/tmp/repo -Pdist release:perform`
8. Create release at https://github.com/jolokia/jolokia/releases (`target/checkout/assembly/target`: `.tar.gz`, `.zip`, `.asc` and `agent/jvm/target`: `.deb`, `.asc`)
9. Handle the release at https://oss.sonatype.org/#stagingRepositories
10. If NPM packages are released:
```console
cd client/javascript-esm
yarn npm login --publish
yarn install
cd packages/jolokia
yarn npm publish
cd ../jolokia-simple
yarn npm publish
```
11. After release is ready, set new version of Site skin in `src/site/site.xml`
12. `mvn clean site -N -Pdist`
```console
git checkout gh-pages
# copy changed site
# change SNAPSHOT version - should be done better...
grep -lr 2.2.6-SNAPSHOT | xargs sed -i 's/2.2.6-SNAPSHOT/2.2.5/g'
git add .
git commit -m '[site] Generate new Jolokia site'
git push origin HEAD
```

[1]: https://www.selenium.dev
[2]: https://issues.apache.org/jira/browse/MRELEASE-798
[3]: https://qunitjs.com
[4]: https://github.com/jolokia/jolokia/issues
[5]: https://www.npmjs.com
[6]: https://central.sonatype.org/publish/
[7]: https://github.com/eirslett/frontend-maven-plugin
[8]: https://rollupjs.org/
