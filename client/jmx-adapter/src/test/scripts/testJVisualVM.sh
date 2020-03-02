#!/usr/bin/env bash

#for instance test with: service:jmx:jolokia://localhost:8780/jolokia/
if [ "$JAVA_HOME" = "" ]
then
  echo "JAVA_HOME must be set and valid to run $0" ; exit 1
fi

VERSION=1.6.3-SNAPSHOT

$JAVA_HOME/bin/java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1046 \
-classpath "$(dirname $0)/../../../target/jolokia-jmx-adapter-${VERSION}-standalone.jar:$JAVA_HOME/lib/visualvm/platform/lib/boot.jar:$JAVA_HOME/lib/visualvm/platform/lib/org-openide-modules.jar:$JAVA_HOME/lib/visualvm/platform/lib/org-openide-util-lookup.jar:$JAVA_HOME/lib/visualvm/platform/lib/org-openide-util.jar:$JAVA_HOME/lib/visualvm/platform/lib/locale/boot_ja.jar:$JAVA_HOME/lib/visualvm/platform/lib/locale/boot_zh_CN.jar:$JAVA_HOME/lib/visualvm/platform/lib/locale/org-openide-modules_ja.jar:$JAVA_HOME/lib/visualvm/platform/lib/locale/org-openide-modules_zh_CN.jar:$JAVA_HOME/lib/visualvm/platform/lib/locale/org-openide-util-lookup_ja.jar:$JAVA_HOME/lib/visualvm/platform/lib/locale/org-openide-util-lookup_zh_CN.jar:$JAVA_HOME/lib/visualvm/platform/lib/locale/org-openide-util_ja.jar:$JAVA_HOME/lib/visualvm/platform/lib/locale/org-openide-util_zh_CN.jar:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar" \
-javaagent:"$(dirname $0)/../../../../../agent/jvm/target/jolokia-jvm-${VERSION}-agent.jar=port=8780" \
-Dnetbeans.dirs=$JAVA_HOME/lib/visualvm/visualvm:$JAVA_HOME/lib/visualvm/profiler: \
-Dnetbeans.home=$JAVA_HOME/lib/visualvm/platform \
-Xms24m \
-Xmx256m \
-Dsun.jvmstat.perdata.syncWaitMs=10000 \
-Dsun.java2d.noddraw=true \
-Dsun.java2d.d3d=false \
-Dnetbeans.keyring.no.master=true \
-Dplugin.manager.install.global=false \
--add-exports=java.desktop/sun.awt=ALL-UNNAMED \
--add-exports=jdk.jvmstat/sun.jvmstat.monitor.event=ALL-UNNAMED \
--add-exports=jdk.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED \
--add-exports=java.desktop/sun.swing=ALL-UNNAMED \
--add-exports=jdk.attach/sun.tools.attach=ALL-UNNAMED \
--add-modules=java.activation \
-XX:+IgnoreUnrecognizedVMOptions \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/tmp/heapdump.hprof \
org.netbeans.Main \
--cachedir /tmp --userdir /Users/marska --branding visualvm

