# How to release Jolokia

## Build and test

* Build the project

```console
mvn clean install
```

* Check integration tests (with `jmx4perl/it/it.pl`)
* Check JavaScript tests

```console
mvn clean install jetty:run -pl examples/client-javascript-test-app
firefox http://localhost:8080/jolokia-all-test.html
```

## Increase version numbers

Make changes to the following files and check in and push.

```console
vi src/docbkx/index.xml
vi agent/core/src/main/java/org/jolokia/Version.java
vi pom.xml
# --> <currentStableVersion>2.0.0</currentStableVersion>
#     <currentSnapshotVersion>2.0.1-SNAPSHOT</currentSnapshotVersion>
vi src/changes/changes.xml # and update date
```

### JavaScript client version

For the JavaScript client project, `npm version` should automatically update the version in `package.json`, `jolokia.js`, and `jolokia-cubism.js`.

```console
cd client/javascript/
npm install
npm version <major|minor|patch>
```

Check in and push the changes.

## Deploy and release

### Build release and deploy it on labs

```console
git clone git@github.com:jolokia/jolokia.git -b 2.0
cd jolokia
mvn -Dmaven.repo.local=/tmp/repo \
    -DdevelopmentVersion=2.0.1-SNAPSHOT \
    -DreleaseVersion=2.0.0 \
    -Dtag=v2.0.0 \
    -Pdist release:prepare
mvn -Dmaven.repo.local=/tmp/repo \
    -DdevelopmentVersion=2.0.1-SNAPSHOT \
    -DreleaseVersion=2.0.0 \
    -Dtag=v2.0.0 \
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
mvn -Dmaven.repo.local=/tmp/repo -Pdist deploy
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


