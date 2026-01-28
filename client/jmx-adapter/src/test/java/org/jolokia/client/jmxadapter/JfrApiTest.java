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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
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
import jdk.management.jfr.ConfigurationInfo;
import jdk.management.jfr.FlightRecorderMXBean;
import jdk.management.jfr.RemoteRecordingStream;
import org.jolokia.jvmagent.JolokiaServer;
import org.jolokia.jvmagent.JolokiaServerConfig;
import org.jolokia.test.util.EnvTestUtil;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * This test shows how to use JFR API directly from Java code. Most of the tests are ignored - the purpose
 * is the API showcase, not actually using JFR.
 */
public class JfrApiTest {

    private JolokiaServer server;
    private MBeanServer platform;

    JMXConnector connector;

    private ObjectName jfr;

    @BeforeClass
    public void startJVMAgent() throws Exception {
        int port = EnvTestUtil.getFreePort();
        JolokiaServerConfig config = new JolokiaServerConfig(Map.of(
            "port", Integer.toString(port),
            "debug", "false"
        ));
        server = new JolokiaServer(config);
        server.start(false);

        platform = ManagementFactory.getPlatformMBeanServer();

        jfr = ObjectName.getInstance(FlightRecorderMXBean.MXBEAN_NAME);

        if (!platform.isRegistered(jfr)) {
            throw new SkipException("JFR MBean not registered: " + jfr);
        }

        JMXServiceURL serviceURL = new JMXServiceURL("jolokia+http", "127.0.0.1", port, "/jolokia");
        connector = JMXConnectorFactory.connect(serviceURL);
    }

    @AfterClass
    public void stopJVMAgent() throws IOException {
        connector.close();
        server.stop();
    }

    // ---- Tests using JFR API without JMX

    /**
     * Low level - deal with {@link Recording} directly. An equivalent of:<ul>
     *     <li>{@code jcmd <pid> JFR.start} + {@code jcmd <pid> JFR.dump} + {@code jcmd <pid> JFR.stop}</li>
     *     <li>{@code jdk.jfr.internal.dcmd.DCmdStart#execute()}</li>
     * </ul>
     * {@link Recording} provides means to configure, start, stop and dump recording data to disk.
     * @throws IOException
     * @throws ParseException
     * @throws InterruptedException
     */
    @Test
    @Ignore
    public void startStopAndDumpRecording() throws IOException, ParseException, InterruptedException {
        Configuration c = Configuration.getConfiguration("default");
        try (Recording r = new Recording(c)) {
            r.start();
            System.gc();
            Thread.sleep(300);
            r.stop();
            Path file = Files.createTempFile("my-recording", ".jfr");
            // this is what Recording is for - to dump data.
            r.dump(file);
            System.out.println("dump file created: " + file.toFile().getAbsolutePath() + " (size: " +  file.toFile().length() + ")");
        }
    }

    /**
     * {@link RecordingStream} holds a reference to a {@link Recording}, so it can eventually {@link RecordingStream#dump(Path)}
     * the events, but mostly it's used to subscribe to events. A {@link RecordingStream} produces events from the
     * current JVM (Java Virtual Machine).
     *
     * @throws IOException
     * @throws ParseException
     */
    @Test
    @Ignore
    public void recordingStream() throws IOException, ParseException, InterruptedException {
        Configuration c = Configuration.getConfiguration("default");
        try (RecordingStream rs = new RecordingStream(c)) {
            // when calling enable() here, in jdk.jfr.internal.PlatformRecording.setSetting() I already
            // see 131 enabled events and 40 disabled events
            rs.enable("jdk.CPULoad").withPeriod(Duration.ofMillis(200));
            rs.onEvent("jdk.GarbageCollection", System.out::println);
            rs.onEvent("jdk.CPULoad", System.out::println);
            rs.onEvent("jdk.JVMInformation", System.out::println);
            rs.startAsync();
            Thread.sleep(1000);
            Path file = Files.createTempFile("my-recording", ".jfr");
            // RecordingStream is mostly for observing events, but dump works too:
            // Without dump() you’d need:
            //  - one RecordingStream for live events
            //  - one Recording for dumping
            // this is fine for RecordingStream when the file exists:
            //    at jdk.jfr.internal.WriteableUserPath.<init>(WriteableUserPath.java:73)
            //    at jdk.jfr.Recording.dump(Recording.java:387)
            //    at jdk.jfr.consumer.RecordingStream.dump(RecordingStream.java:441)
            //      - locked <0x1389> (a jdk.jfr.internal.PlatformRecorder)
            //    at org.jolokia.client.jmxadapter.JfrApiTest.recordingStream(JfrApiTest.java:154)
            rs.dump(file);
            System.out.println("dump file created: " + file.toFile().getAbsolutePath() + " (size: " +  file.toFile().length() + ")");
        }
    }

    // ---- Tests using JFR API with JMX used internally

    /**
     * {@link RemoteRecordingStream} can be used for remote monitoring after passing {@link MBeanServerConnection}.
     * Internally it uses {@link FlightRecorderMXBean} - remote or local.
     *
     * @throws IOException
     */
    @Test
    @Ignore
    public void remoteRecordingStream() throws IOException, InterruptedException {
        // RemoteRecordingStream constructor does:
        // - javax.management.JMX.newMXBeanProxy() for jdk.management.jfr.FlightRecorderMXBean
        // - jdk.management.jfr.FlightRecorderMXBean.newRecording()
        // - jdk.management.jfr.FlightRecorderMXBean.setRecordingOptions("name", ...)
        // - creates stream = jdk.jfr.internal.consumer.EventDirectoryStream
        // - creates jdk.management.jfr.DiskRepository
        try (RemoteRecordingStream rs = new RemoteRecordingStream(platform)) {
            // rs.onEvent -> jdk.jfr.consumer.EventStream.onEvent()
            // looks like without enable(), RemoteRecordingStream doesn't get any events - unlike
            // as with RecordingStream. Here, this call leads to jdk.jfr.internal.PlatformRecording.setSettings()
            // call via JMX (with full state), and the jdk.jfr.internal.PlatformRecording.settings is an empty map
            rs.enable("jdk.CPULoad").withPeriod(Duration.ofMillis(200));
            rs.onEvent("jdk.GarbageCollection", System.out::println);
            rs.onEvent("jdk.CPULoad", System.out::println);
            rs.onEvent("jdk.JVMInformation", System.out::println);
            // jdk.jfr.consumer.EventStream.startAsync is asynchronous
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
//            rs.start();
            Thread.sleep(300);
            // RemoteRecordingStream.dump() fails with existing file:
            //    at jdk.management.jfr.FileDump.write(FileDump.java:89)
            //    at jdk.management.jfr.RemoteRecordingStream.dump(RemoteRecordingStream.java:610)
            //    at org.jolokia.client.jmxadapter.JfrApiTest.remoteRecordingStream(JfrApiTest.java:202)
            Path file = Path.of(System.getProperty("java.io.tmpdir"), System.nanoTime() + ".jfr");
            rs.dump(file);
            assertTrue(file.toFile().length() > 0);
//            System.out.println("dump file created: " + file.toFile().getAbsolutePath() + " (size: " +  file.toFile().length() + ")");
        }
    }

    // ---- Tests using JFR API through jdk.management.jfr:type=FlightRecorder MBean and local MBeanServer
    //      https://openjdk.org/jeps/349

    @Test
    @Ignore
    public void startDumpAndStopRecordingUsingJMX() throws Exception {
        MBeanInfo info = platform.getMBeanInfo(jfr);

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
        TabularDataSupport td = new TabularDataSupport(type);

        long id = (long) platform.invoke(jfr, "newRecording", new Object[0], new String[0]);
        long start = Instant.now().minusSeconds(1).toEpochMilli();
        platform.invoke(jfr, "startRecording", new Object[] { id }, new String[] { "long" });
        Thread.sleep(300);
        platform.invoke(jfr, "stopRecording", new Object[] { id }, new String[] { "long" });
        long end = Instant.now().toEpochMilli();

        long cloneId = (long) platform.invoke(jfr, "cloneRecording", new Object[] { id, true }, new String[] { "long", "boolean" });

        // jdk.management.jfr.FlightRecorderMXBean.openStream() accepts long and a Map. The Map should
        // be passed as TabularData and there are three ways of adding "rows"
        // 1. put(CompositeData) easy way - CompositeDataSupport with a Map
//        td.put(new CompositeDataSupport(type.getRowType(), Map.of("key", "streamVersion", "value", "1.0")));
//        td.put(new CompositeDataSupport(type.getRowType(), Map.of("key", "asd", "value", "dsa")));
        // 2. put(CompositeData) mid-level way - CompositeDataSupport with item names and item values
//        td.put(new CompositeDataSupport(type.getRowType(), new String[] { "key", "value" }, new Object[] { "streamVersion", "1.0" }));
//        td.put(new CompositeDataSupport(type.getRowType(), new String[] { "key", "value" }, new Object[] { "asd", "dsa" }));
        // 3. put(index values, CompositeData) - as above, but specifying the index separately
//        td.put("streamVersion", new CompositeDataSupport(type.getRowType(), new String[] { "key", "value" }, new Object[] { "streamVersion", "1.0" }));
//        td.put("asd", new CompositeDataSupport(type.getRowType(), new String[] { "key", "value" }, new Object[] { "asd", "dsa" }));
//        td.put("startTime", new CompositeDataSupport(type.getRowType(), new String[] { "key", "value" }, new Object[] { "startTime", Long.toString(start) }));
//        td.put("endTime", new CompositeDataSupport(type.getRowType(), new String[] { "key", "value" }, new Object[] { "endTime", Long.toString(end) }));
        // now we can pass the TabularData to a call
        long stream = (long) platform.invoke(jfr, "openStream", new Object[] { cloneId, td }, new String[] { "long", "javax.management.openmbean.TabularData" });

        byte[] dump;
        int size = 0;
        while ((dump = (byte[]) platform.invoke(jfr, "readStream", new Object[] { stream }, new String[] { "long" })) != null) {
            size += dump.length;
        }
        platform.invoke(jfr, "closeStream", new Object[] { stream }, new String[] { "long" });
        platform.invoke(jfr, "closeRecording", new Object[] { cloneId }, new String[] { "long" });

        assertTrue(size > 0);
    }

    @Test
    @Ignore
    public void remoteRecordingStreamWithPlatformServerAndJMXProxy() throws Exception {
        FlightRecorderMXBean jfr = JMX.newMXBeanProxy(ManagementFactory.getPlatformMBeanServer(), this.jfr, FlightRecorderMXBean.class);
        long id = jfr.newRecording();
        jfr.startRecording(id);
        Thread.sleep(500);
        jfr.stopRecording(id);
        // com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.TabularMapping.toNonNullOpenValue() will convert
        // a map to proper CompositeType associated with TabularType
        // com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory.keyValueArray is ALWAYS [ "key", "value" ]
//        long stream = jfr.openStream(id, Collections.emptyMap());
        // do NOT set "streamVersion" = "1.0" - this is to read data from running recording and we won't
        // get the "null response means end of stream" - we'll be getting 0-length array instead.
//        long stream = jfr.openStream(id, Map.of("streamVersion", "1.0"));
        long stream = jfr.openStream(id, Collections.emptyMap());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] dump;
        while ((dump = jfr.readStream(stream)) != null) {
            baos.write(dump);
        }
        jfr.closeStream(stream);
        jfr.closeRecording(id);

        assertTrue(baos.toByteArray().length > 0);
    }

    // ---- Tests using JFR API with Jolokia MBeanServerConnection (API and direct JMX)

    @Test
    public void remoteRecordingStreamWithJolokia() throws IOException, InterruptedException {
        MBeanServerConnection conn = connector.getMBeanServerConnection();

        try (RemoteRecordingStream rs = new RemoteRecordingStream(conn)) {
            rs.enable("jdk.GCPhasePause").withoutThreshold();
            rs.enable("jdk.CPULoad").withPeriod(Duration.ofSeconds(1));
            rs.onEvent("jdk.CPULoad", System.out::println);
            rs.onEvent("jdk.GCPhasePause", System.out::println);
            rs.startAsync();
            Thread.sleep(300);
        }
    }

    @Test
    public void startDumpAndStopRecordingUsingJMXWithJolokia() throws Exception {
        MBeanServerConnection jolokia = connector.getMBeanServerConnection();
        MBeanInfo info = jolokia.getMBeanInfo(jfr);

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
        TabularDataSupport td = new TabularDataSupport(type);

        long id = (long) jolokia.invoke(jfr, "newRecording", new Object[0], new String[0]);
        long start = Instant.now().minusSeconds(1).toEpochMilli();
        jolokia.invoke(jfr, "startRecording", new Object[] { id }, new String[] { "long" });
        Thread.sleep(300);
        jolokia.invoke(jfr, "stopRecording", new Object[] { id }, new String[] { "long" });
        long end = Instant.now().toEpochMilli();

        long cloneId = (long) jolokia.invoke(jfr, "cloneRecording", new Object[] { id, true }, new String[] { "long", "boolean" });

        td.put(new CompositeDataSupport(type.getRowType(), Map.of("key", "startTime", "value", Long.toString(start))));
        long stream = (long) jolokia.invoke(jfr, "openStream", new Object[] { cloneId, td }, new String[] { "long", "javax.management.openmbean.TabularData" });

        byte[] dump;
        int size = 0;
        while ((dump = (byte[]) jolokia.invoke(jfr, "readStream", new Object[] { stream }, new String[] { "long" })) != null) {
            size += dump.length;
        }
        jolokia.invoke(jfr, "closeStream", new Object[] { stream }, new String[] { "long" });
        jolokia.invoke(jfr, "closeRecording", new Object[] { cloneId }, new String[] { "long" });

        assertTrue(size > 0);
    }

    @Test
    public void remoteRecordingStreamWithPlatformServerAndJMXProxyWithJolokia() throws Exception {
        MBeanServerConnection jolokia = platform;//connector.getMBeanServerConnection();

        FlightRecorderMXBean jfr = JMX.newMXBeanProxy(jolokia, this.jfr, FlightRecorderMXBean.class);
        long id = jfr.newRecording();

        // see https://bugs.openjdk.org/browse/JDK-8308877
        // fixed in https://github.com/openjdk/jdk/commit/5fdb22f911b7e430bc1a621f6a39266ee2e50eda
//        List<EventTypeInfo> eventTypes = jfr.getEventTypes();
//        assertTrue(eventTypes != null && !eventTypes.isEmpty());

        List<ConfigurationInfo> configurations = jfr.getConfigurations();
        assertTrue(configurations != null && !configurations.isEmpty());

        Map<String, String> options = jfr.getRecordingOptions(id);
        assertNotNull(options);
        Map<String, String> settings = jfr.getRecordingSettings(id);
        assertNotNull(settings);

        jfr.startRecording(id);
        Thread.sleep(500);
        jfr.stopRecording(id);
        long stream = jfr.openStream(id, Collections.emptyMap());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] dump;
        while ((dump = jfr.readStream(stream)) != null) {
            baos.write(dump);
        }
        jfr.closeStream(stream);
        jfr.closeRecording(id);

        assertTrue(baos.toByteArray().length > 0);
    }

}
