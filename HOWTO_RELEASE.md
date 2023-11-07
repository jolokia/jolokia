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

This can be fixed by proper firewall configuration. Here's an example of `nft` command on Fedora with `FedoraServer` default zone:
```console
# firewall-cmd --get-default-zone 
FedoraServer

# nft add rule inet firewalld filter_IN_FedoraServer_allow ip daddr 239.192.48.84 accept

# nft -a list chain inet firewalld filter_IN_FedoraServer_allow
table inet firewalld {
	chain filter_IN_FedoraServer_allow { # handle 206
		...
		ip daddr 239.192.48.84 accept # handle 501
	}
}

(run the tests)

# nft delete rule inet firewalld filter_IN_FedoraServer_allow handle 501
```

There are however additional tests that should be run a bit outside of standard `mvn clean install`.

Instead of complex configuration that would be required for frameworks like [Selenium][1], Javascript tests are run
from the web browser after running examples/client-javascript-test-app application:

```console
$ mvn clean package -DskipTests jetty:run-war -f examples/client-javascript-test-app
[INFO] Scanning for projects...
[INFO] 
[INFO] -------< org.jolokia:jolokia-example-client-javascript-test-app >-------
[INFO] Building jolokia-example-client-javascript-test-app 2.0.0-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ war ]---------------------------------
...
[INFO] Started o.e.j.m.p.MavenWebAppContext@11cc9e1e{JSON JMX Agent,/,file:///data/sources/github.com/jolokia/jolokia/examples/client-javascript-test-app/target/jolokia-example-client-javascript-test-app-2.0.0-SNAPSHOT/,AVAILABLE}{/data/sources/github.com/jolokia/jolokia/examples/client-javascript-test-app/target/jolokia-example-client-javascript-test-app-2.0.0-SNAPSHOT.war}
[INFO] Started ServerConnector@714b6999{HTTP/1.1, (http/1.1)}{0.0.0.0:8080}
[INFO] Started Server@59e0d521{STARTING}[11.0.16,sto=0] @5879ms
[INFO] Scan interval ms = 10
```

JavaScript tests are run by browsing to one of:
* http://localhost:8080/jolokia-test.html - tests for `jolokia.js`
* http://localhost:8080/jolokia-simple-test.html - tests for `jolokia-simple.js`
* http://localhost:8080/jolokia-poller-test.html - tests for polling part of `jolokia.js`
* http://localhost:8080/jolokia-all-test.html - all tests combined.

JavaScript tests are run with the help of [QUnit][3]. To make the work smoother without a need to rebuild `client/javascript`
and `examples/client-javascript-test-app`, there's one handy `makeLinks.sh` shell script (for Linux) that replaces the target
files with symbolic links to original locations.

Just `cd` into correct directory and run the script:
```console
$ cd examples/client-javascript-test-app/
 
$ ./makeLinks.sh 
+ cd target/
++ find . -type d -name 'jolokia-example-client-javascript-test-app-*'
+ WARUNPACKED=./jolokia-example-client-javascript-test-app-2.0.0-SNAPSHOT
+ '[' -d ./jolokia-example-client-javascript-test-app-2.0.0-SNAPSHOT ']'
+ cd ./jolokia-example-client-javascript-test-app-2.0.0-SNAPSHOT
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
...
```

With the symbolic links created, just change the test files or `jolokia.js` in your IDE of choice and re-run the tests.

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

### Maven version change

This is done automatically by using `maven-release-plugin`. No manual work should be required.

### Current stable version change

After successful release, main `pom.xml` should have `<currentStableVersion>` property updated to match the
latest Maven version set by `maven-release-plugin`. Then we can continue work on documentation. There's no need to
set this version anywhere else, because it's propagated through Maven properties to maven plugins that generate the
site (like maven-site-plugin or `npx` invocation through `frontend-maven-plugin`).

### Jolokia protocol version change

This is done during normal development and such change is never part of the release process itself. Protocol version
change should be related to particular [GitHub issue][4].

### JavaScript package/client version change

This can be done just before the release (recommended), but if there are only JavaScript code changes and we want to
deploy new Jolokia JavaScript client package to [NPM Registry][5], it's enough to:

* run `npm version <new version>` command
* commit and push the changes

`npm version` updates version in `package.json`, but additionally (see `man npm version`) runs commands from
`scripts/version` field of `package.json`:

```json
{
  "scripts": {
    "version": "node --no-warnings ./scripts/update-version.mjs"
  }
}
```

We'll get `client/javascript/src/main/javascript/jolokia.js` and `client/javascript/src/main/javascript/jolokia-cubism.js`
updated as well.

## Update the changelog

Before the release it's worth updating the changelog in `src/changes/changes.xml`, but it can also be done after
the release, because this XML file is processed during `mvn site` generation.

## Deploy and release

The important part of the release is to sign the artifacts with your GPG key. GPG keys can be checked using `gpg -k`
command and the output should contains something like:

```console
$ gpg -k
/home/ggrzybek/.gnupg/pubring.kbx
---------------------------------
...
pub   rsa4096 2020-09-02 [SC]
      4C8BD038EE847AB3A6A586EEDB593BFCBBDC099E
...
```

Last 8 bytes of the public key fingerprint is the key ID. Here it's `DB593BFCBBDC099E`. This value should be set/used
in `-Dgpg.keyname` parameter for `maven-gpg-plugin`. Adding `-Dgpg.useagent=true` will open OS specific dialog to
provide key password (it is `true` by default).

### Build release and deploy it to OSSRH

Jolokia uses Sonatype's Open Source Software Repository Hosting (OSSRH) service to deploy artifacts to Maven Central.
Please check instructions and more information [here][6].

```console
git clone git@github.com:jolokia/jolokia.git -b 2.0
cd jolokia
mvn -Dmaven.repo.local=/tmp/repo \
    -DdevelopmentVersion=2.0.1-SNAPSHOT \
    -DreleaseVersion=2.0.0 \
    -Dtag=v2.0.0 \
    -Dgpg.keyname=roland@jolokia.org \
    -Pdist release:prepare
mvn -Dmaven.repo.local=/tmp/repo \
    -Pdist release:perform
```

### Copy assembly to GitHub

* Create a new release ("Draft a release" - Button)
* Upload `tar.gz` and `zip` files from `target/checkout/src/assembly/target`
* Upload all JavaScript files in `target/checkout/client/javascript/target`:
    * `compressed/jolokia-*.js`
    * `scripts/jolokia*.js`
* Upload JVM debian package from
    * `agent/jvm/target/*.deb`

### Deploy to Sonatype staging

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

Before performing the following commands, make sure you've logged in to the [NPM registry](https://www.npmjs.com/) with an account that has publish privilege to [jolokia.js](https://www.npmjs.com/package/jolokia.js) NPM package. 

```console
cd client/javascript
npm install
npm publish
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
md5 -q src/site/resources/jolokia.meta > src/site/resources/jolokia.meta.md5
```

## Recreate website (`~/jolokia`)

### Update version number in `site.xml` to next dev version

```console
vi ./src/site/site.xml
```

### Update Skin version to next snapshot

```console
vi tools/site-skin/pom.xml
git commit -a
```

### Adapted tracking code

```console
git co analytics
git rebase master
cd tools/site-skin; mvn clean install;
cd ../..
mvn clean install
mvn -N -Pdist site
```

## Snapshot release

* Set version number in `Version.java`, `jolokia.js`, `jolokia-cubism.js`, `test-app/pom.xml`, `docbkx/index.xml`

```console
mvn -Pdist deploy
```

* Adapt URI and version number in root-repository.xml on labs.consol.de to us a httppgp URL

[1]: https://www.selenium.dev
[2]: https://issues.apache.org/jira/browse/MRELEASE-798
[3]: https://qunitjs.com
[4]: https://github.com/jolokia/jolokia/issues
[5]: https://www.npmjs.com
[6]: https://central.sonatype.org/publish/