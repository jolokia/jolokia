#!/bin/bash

#for instance test with: service:jmx:kubernetes:///mynamespace/mypodname-.*/actuator/jolokia/
if [ "$JAVA_HOME" = "" ]
then
  echo "JAVA_HOME must be set and valid to run $0" ; exit 1
fi

VERSION=1.6.3-SNAPSHOT

$JAVA_HOME/bin/java -Dcom.sun.management.jmxremote \
-Dcom.sun.management.jmxremote.port=45999 \
-Dcom.sun.management.jmxremote.authenticate=false \
-Dcom.sun.management.jmxremote.ssl=false \
-Djava.rmi.server.hostname=localhost \
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1045 \
-javaagent:"$(dirname $0)/../../../../../agent/jvm/target/jolokia-jvm-${VERSION}.jar=port=8779" \
-classpath "$(dirname $0)/../../../../jmx-adapter/target/jolokia-jmx-adapter-${VERSION}-standalone.jar:$(dirname $0)/../../../target/jolokia-kubernetes-1.6.3-SNAPSHOT-standalone.jar:$HOME/.m2/repository/io/kubernetes/client-java/5.0.0/client-java-5.0.0.jar:$JAVA_HOME/lib/jconsole.jar:$JAVA_HOME/classes" \
-Dapplication.home=$JAVA_HOME \
-Xms8m -Djconsole.showOutputViewer \
sun.tools.jconsole.JConsole
