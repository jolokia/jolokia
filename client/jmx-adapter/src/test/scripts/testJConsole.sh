#!/bin/bash

#for instance test with: service:jmx:jolokia://localhost:8779/jolokia/
if [ "$JAVA_HOME" = "" ]
then
  echo "JAVA_HOME must be set and valid to run $0" ; exit 1
fi

VERSION=1.6.3-SNAPSHOT

$JAVA_HOME/bin/java -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=45999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=localhost -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1045 -javaagent:"$(dirname $0)/../../../../../agent/jvm/target/jolokia-jvm-${VERSION}-agent.jar=port=8779" -classpath "$(dirname $0)/../../../target/jolokia-jmx-adapter-${VERSION}-standalone.jar:$JAVA_HOME/lib/jconsole.jar:$JAVA_HOME/classes" -Dapplication.home=$JAVA_HOME -Xms8m -Djconsole.showOutputViewer sun.tools.jconsole.JConsole
