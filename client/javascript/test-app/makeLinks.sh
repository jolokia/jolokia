#!/bin/sh -x


cd target/
dir=`ls -d jolokia-client* | grep -v .war`
cd $dir;
rm jolokia-test.html
ln -s ../../src/main/webapp/jolokia-test.html .
rm jolokia-simple-test.html
ln -s ../../src/main/webapp/jolokia-simple-test.html .
rm jolokia-poller-test.html
ln -s ../../src/main/webapp/jolokia-poller-test.html .
rm jolokia-all-test.html
ln -s ../../src/main/webapp/jolokia-all-test.html .
cd scripts/lib
rm jolokia.js
ln -s ../../../../../src/main/javascript/jolokia.js .
rm jolokia-simple.js
ln -s ../../../../../src/main/javascript/jolokia-simple.js .
cd ../test
rm jolokia-test.js
ln -s ../../../../src/main/javascript/test/jolokia-test.js .
rm jolokia-simple-test.js
ln -s ../../../../src/main/javascript/test/jolokia-simple-test.js .
rm jolokia-poller-test.js
ln -s ../../../../src/main/javascript/test/jolokia-poller-test.js .

cd ../../../..

# Create symbolic link for fast unit test development
# Workflow:
# mvn clean install
# sh makeLinks.sh
# mvn jetty:run-exploded

