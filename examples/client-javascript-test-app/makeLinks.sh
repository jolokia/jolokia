#!/bin/sh -x

# This script makes it easier to work on QUnit tests executed in a browser after running:
# $ cd examples/client-javascript-test-app
# $ mvn clean package jetty:run-war
#
# On first Maven build:
#  - com.devspan.mojo.javascript:javascript-maven-plugin:war-package goal unpacks org.jolokia:jolokia-client-javascript jar
#    and puts jolokia.js, jolokia-simple.js and jolokia-cubism.js into target/<web-archive-location>/scripts/lib
#  - org.eclipse.jetty:jetty-maven-plugin:run-war goal starts Jetty pointing to target/<web-archive-location>
#    web application
#
# When working on either the tests or main Jolokia JavaScript libraries, it'd be much faster if we avoid unpacking the
# libraries and redeploying Jetty.
# So we can simply create symbolic links to relevant files

cd target/ || { echo "Please run \"mvn clean package jetty:run-war\" first" && exit; }

WARUNPACKED=$(find . -type d -name "jolokia-example-client-javascript-test-app-*")

if [ -d "$WARUNPACKED" ]; then
  cd "$WARUNPACKED" || { echo "Can't CD into $WARUNPACKED" && exit; }
else
  echo "Please run \"mvn clean package jetty:run-war\" first"
  exit
fi

rm jolokia-all-test.html
rm jolokia-chat.html
rm jolokia-poller-test.html
rm jolokia-simple-test.html
rm jolokia-test.html
rm demo/plot.html
ln -s ../../src/main/webapp/jolokia-all-test.html .
ln -s ../../src/main/webapp/jolokia-chat.html .
ln -s ../../src/main/webapp/jolokia-poller-test.html .
ln -s ../../src/main/webapp/jolokia-simple-test.html .
ln -s ../../src/main/webapp/jolokia-test.html .
ln -s ../../src/main/webapp/demo/plot.html demo

cd scripts/lib || { echo "Can't CD into scripts/lib" && exit; }
rm jolokia.js
rm jolokia-cubism.js
rm jolokia-simple.js
ln -s ../../../../../../client/javascript/src/main/javascript/jolokia.js .
ln -s ../../../../../../client/javascript/src/main/javascript/jolokia-cubism.js .
ln -s ../../../../../../client/javascript/src/main/javascript/jolokia-simple.js .

cd ../test || { echo "Can't CD into ../test" && exit; }
rm jolokia-poller-test.js
rm jolokia-simple-test.js
rm jolokia-test.js
ln -s ../../../../src/main/javascript/test/jolokia-poller-test.js .
ln -s ../../../../src/main/javascript/test/jolokia-simple-test.js .
ln -s ../../../../src/main/javascript/test/jolokia-test.js .
