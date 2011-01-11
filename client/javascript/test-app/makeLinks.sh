#!/bin/sh -x

# Create symbolic link for fast unit test development
# Workflow:
# mvn clean install
# sh makeLinks.sh
# mvn jetty:war-exploded

cd target/
dir=`ls -d jolokia-client* | grep -v .war`
cd $dir;
rm jolokia-test.html
ln -s ../../src/main/webapp/jolokia-test.html .
rm jolokia-simple-test.html
ln -s ../../src/main/webapp/jolokia-simple-test.html .
rm jolokia-all-test.html
ln -s ../../src/main/webapp/jolokia-all-test.html .
cd scripts/lib
rm jolokia.js
ln -s ../../../../../src/main/javascript/jolokia.js .
rm jolokia-simple.js
ln -s ../../../../../src/main/javascript/jolokia-simple.js .
cd ../../../..
