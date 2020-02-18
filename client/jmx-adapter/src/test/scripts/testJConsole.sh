#!/bin/bash

#for instance test with: service:jmx:jolokia://localhost:9091/hawtio/jolokia/

java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1045  -javaagent:../../../../../agent/jvm/target/jolokia-jvm-1.6.3-SNAPSHOT-agent.jar=port=8779 -classpath ../../../target/jmx-adapter-1.6.3-SNAPSHOT-standalone.jar:$JAVA_HOME/lib/jconsole.jar:$JAVA_HOME/classes -Dapplication.home=$JAVA_HOME -Xms8m -Djconsole.showOutputViewer sun.tools.jconsole.JConsole
