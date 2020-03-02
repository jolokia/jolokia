#!/bin/bash
#Run JConsole with support for Jolokia
if [ "$JAVA_HOME" = "" ]
then
  echo "JAVA_HOME must be set and valid to run $0" ; exit 1
fi

VERSION=1.6.3-SNAPSHOT

$JAVA_HOME/bin/java -classpath "$(dirname $0)/../../../target/jolokia-jmx-adapter-${VERSION}-standalone.jar:$JAVA_HOME/lib/jconsole.jar:$JAVA_HOME/classes" -Dapplication.home=$JAVA_HOME -Xms8m -Djconsole.showOutputViewer sun.tools.jconsole.JConsole
