#!/bin/bash

#for instance test with: service:jmx:jolokia://localhost:8779/jolokia/



java -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=45999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=localhost -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1045 -javaagent:"$(dirname $0)/../../../../../agent/jvm/target/jolokia-jvm-1.6.3-SNAPSHOT-agent.jar=port=8779" -classpath "$(dirname $0)/../../../target/jmx-adapter-1.6.3-SNAPSHOT-standalone.jar:$JAVA_HOME/lib/jconsole.jar:$JAVA_HOME/classes" -Dapplication.home=$JAVA_HOME -Xms8m -Djconsole.showOutputViewer sun.tools.jconsole.JConsole
