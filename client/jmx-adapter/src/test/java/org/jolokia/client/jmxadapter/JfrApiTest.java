/*
 * Copyright 2009-2025 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jolokia.client.jmxadapter;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Duration;
import java.util.Map;
import javax.management.JMX;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.OpenMBeanParameterInfo;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingStream;
import jdk.management.jfr.FlightRecorderMXBean;
import jdk.management.jfr.RemoteRecordingStream;
import org.testng.annotations.Ignore;import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

/**
 * This test shows how to use JFR API directly from Java code
 */
@Ignore("Manual test showing JFR API usage")
public class JfrApiTest {

    /**
     * An equivalent of:<ul>
     *     <li>{@code jcmd <pid> JFR.start} + {@code jcmd <pid> JFR.dump} + {@code jcmd <pid> JFR.stop}</li>
     *     <li>{@code jdk.jfr.internal.dcmd.DCmdStart#execute()}</li>
     * </ul>
     * {@link Recording} provides means to configure, start, stop and dump recording data to disk.
     * @throws IOException
     * @throws ParseException
     * @throws InterruptedException
     */
    @Test
    public void startDumpAndStopRecording() throws IOException, ParseException, InterruptedException {
        Configuration c = Configuration.getConfiguration("default");
        try (Recording r = new Recording(c)) {
            r.start();
            System.gc();
            Thread.sleep(5000);
            r.stop();
            r.dump(Files.createTempFile("my-recording", ".jfr"));
            System.out.println("dump file created");
        }
    }

    /**
     * {@link RecordingStream} holds a reference to a {@link Recording}.
     * A recording stream produces events from the current JVM (Java Virtual Machine).
     * @throws IOException
     * @throws ParseException
     */
    @Test
    public void recordingStream() throws IOException, ParseException {
        Configuration c = Configuration.getConfiguration("default");
        try (RecordingStream rs = new RecordingStream(c)) {
            rs.onEvent("jdk.GarbageCollection", System.out::println);
            rs.onEvent("jdk.CPULoad", System.out::println);
            rs.onEvent("jdk.JVMInformation", System.out::println);
            rs.start();
        }
    }

    /**
     * {@link RecordingStream} holds a reference to a {@link Recording}.
     * A recording stream produces events from the current JVM (Java Virtual Machine).
     * @throws IOException
     * @throws ParseException
     */
    @Test
    public void remoteRecordingStream() throws IOException, ParseException, InterruptedException {
        Configuration c = Configuration.getConfiguration("default");
        // RemoteRecordingStream constructor does:
        // - javax.management.JMX.newMXBeanProxy() for jdk.management.jfr.FlightRecorderMXBean
        // - jdk.management.jfr.FlightRecorderMXBean.newRecording()
        // - jdk.management.jfr.FlightRecorderMXBean.setRecordingOptions("name", ...)
        // - creates stream = jdk.jfr.internal.consumer.EventDirectoryStream
        // - creates jdk.management.jfr.DiskRepository
        try (RemoteRecordingStream rs = new RemoteRecordingStream(ManagementFactory.getPlatformMBeanServer())) {
            // rs.onEvent -> jdk.jfr.consumer.EventStream.onEvent()
            rs.onEvent("jdk.GarbageCollection", System.out::println);
            rs.onEvent("jdk.CPULoad", System.out::println);
            rs.onEvent("jdk.JVMInformation", System.out::println);
            // jdk.jfr.consumer.EventStream.startAsync
            // jdk.management.jfr.FlightRecorderMXBean.startRecording()
            // starts jdk.management.jfr.DownLoadThread, which calls:
            // 1x jdk.management.jfr.FlightRecorderMXBean.openStream
            // loop: jdk.management.jfr.FlightRecorderMXBean.readStream + jdk.management.jfr.DiskRepository.write
            // jdk.jfr.internal.consumer.EventDirectoryStream.process() in a thread
            rs.startAsync();
            // jdk.management.jfr.RemoteRecordingStream.start is synchronous
            // jdk.management.jfr.FlightRecorderMXBean.startRecording()
            // starts jdk.management.jfr.DownLoadThread, which calls:
            // 1x jdk.management.jfr.FlightRecorderMXBean.openStream
            // loop: jdk.management.jfr.FlightRecorderMXBean.readStream + jdk.management.jfr.DiskRepository.write
            // jdk.jfr.internal.consumer.EventDirectoryStream.process() in a thread
            rs.start();
            Thread.sleep(1000);
            rs.dump(Paths.get("/tmp/jolokia-" + System.nanoTime() + ".jfr"));
        }
        System.out.println("finish");
    }

    @Test
    public void startDumpAndStopRecordingUsingJMX() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName jfrMBeanName = ObjectName.getInstance("jdk.management.jfr:type=FlightRecorder");
        MBeanInfo info = server.getMBeanInfo(jfrMBeanName);
        System.out.println(info);

        TabularType type = null;
        for (MBeanOperationInfo op : info.getOperations()) {
            if (op.getName().equals("openStream")) {
                MBeanParameterInfo param1 = op.getSignature()[1];
                if (param1 instanceof OpenMBeanParameterInfo openMBeanParameterInfo) {
                    type = (TabularType) openMBeanParameterInfo.getOpenType();
                    break;
                }
            }
        }

        assertNotNull(type);

        long id = (long) server.invoke(jfrMBeanName, "newRecording", new Object[0], new String[0]);
        server.invoke(jfrMBeanName, "startRecording", new Object[] { id }, new String[] { "long" });
        Thread.sleep(1000);
        server.invoke(jfrMBeanName, "stopRecording", new Object[] { id }, new String[] { "long" });
        TabularDataSupport td = new TabularDataSupport(type);
        // three ways of adding "rows"
//        td.put(new CompositeDataSupport(type.getRowType(), Map.of("key", "streamVersion", "value", "1.0")));
//        td.put(new CompositeDataSupport(type.getRowType(), Map.of("key", "asd", "value", "dsa")));
        td.put("streamVersion", new CompositeDataSupport(type.getRowType(), new String[] { "key", "value" }, new Object[] { "streamVersion", "1.0" }));
        td.put("asd", new CompositeDataSupport(type.getRowType(), new String[] { "key", "value" }, new Object[] { "asd", "dsa" }));
//        td.put(new CompositeDataSupport(type.getRowType(), new String[] { "key", "value" }, new Object[] { "streamVersion", "1.0" }));
//        td.put(new CompositeDataSupport(type.getRowType(), new String[] { "key", "value" }, new Object[] { "asd", "dsa" }));
        long stream = (long) server.invoke(jfrMBeanName, "openStream", new Object[] { id, td }, new String[] { "long", "javax.management.openmbean.TabularData" });
        byte[] dump = (byte[]) server.invoke(jfrMBeanName, "readStream", new Object[] { stream }, new String[] { "long" });
        server.invoke(jfrMBeanName, "closeStream", new Object[] { stream }, new String[] { "long" });
        server.invoke(jfrMBeanName, "closeRecording", new Object[] { id }, new String[] { "long" });

        System.out.println("dump size: " + dump.length);
    }

    @Test
    public void startDumpAndStopRecordingUsingJMXProxy() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName jfrMBeanName = ObjectName.getInstance("jdk.management.jfr:type=FlightRecorder");
        MBeanInfo info = server.getMBeanInfo(jfrMBeanName);

        FlightRecorderMXBean jfr = JMX.newMXBeanProxy(server, jfrMBeanName, FlightRecorderMXBean.class);
        long id = jfr.newRecording();
        jfr.startRecording(id);
        Thread.sleep(1000);
        jfr.stopRecording(id);
        // com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.TabularMapping.toNonNullOpenValue() will convert
        // a map to proper CompositeType associated with TabularType
        // com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.keyValueArray is ALWAYS [ "key", "value" ]
        long stream = jfr.openStream(id, Map.of("streamVersion", "1.0"));
        byte[] dump = jfr.readStream(stream);
        jfr.closeStream(stream);
        jfr.closeRecording(id);

        System.out.println("dump size: " + dump.length);
    }

    @Test
    public void remoteRecordingStreamWithJolokiaJMXAdapter() throws IOException {
        String host = "localhost";
        String url = "service:jmx:jolokia://" + host + ":" + 7778 + "/jolokia";

        JMXServiceURL u = new JMXServiceURL(url);
        MBeanServerConnection conn;
        try (JMXConnector c = JMXConnectorFactory.connect(u)) {
            conn = c.getMBeanServerConnection();

            try (RemoteRecordingStream rs = new RemoteRecordingStream(conn)) {
                rs.enable("jdk.GCPhasePause").withoutThreshold();
                rs.enable("jdk.CPULoad").withPeriod(Duration.ofSeconds(1));
                rs.onEvent("jdk.CPULoad", System.out::println);
                rs.onEvent("jdk.GCPhasePause", System.out::println);
                rs.start();
                rs.startAsync();
            }
        }
    }

}
