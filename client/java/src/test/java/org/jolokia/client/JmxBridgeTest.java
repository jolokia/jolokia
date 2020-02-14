package org.jolokia.client;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.core.ThrowingRunnable;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.request.J4pVersionRequest;
import org.jolokia.jmx.JolokiaMBeanServerUtil;
import org.jolokia.jvmagent.JvmAgent;
import org.jolokia.test.util.EnvTestUtil;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.management.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.jayway.awaitility.Awaitility.await;

/** I test the Jolokia Jmx adapter by comparing results with a traditional MBeanConnection */
public class JmxBridgeTest {

  private RemoteJmxAdapter adapter;

  private static final ObjectName RUNTIME =
      RemoteJmxAdapter.getObjectName("java.lang:type=Runtime");

  private static final QueryExp QUERY =
      Query.or(
          Query.anySubString(Query.classattr(), Query.value("Object")),
          Query.anySubString(Query.classattr(), Query.value("String")));

  // attributes that for some reason (typically live data) cannot be used for 1:1 testing between
  // native and jolokia
  private static Collection<String> ATTRIBUTES_NOT_SAFE_FOR_DIRECT_COMPARISON =
      new HashSet<String>(
          Arrays.asList(
              "java.lang:type=Threading.CurrentThreadUserTime",
              "java.lang:type=OperatingSystem.ProcessCpuLoad",
              "java.lang:type=Threading.CurrentThreadCpuTime",
              "java.lang:type=Runtime.FreePhysicalMemorySize",
              "java.lang:type=OperatingSystem.ProcessCpuTime",
              "java.lang:type=MemoryPool,name=Metaspace.PeakUsage",
              "java.lang:type=MemoryPool,name=PS Eden Space.Usage",
              "java.lang:type=MemoryPool,name=Metaspace.Usage",
              "java.lang:type=Memory.NonHeapMemoryUsage",
              "java.lang:type=MemoryPool,name=Code Cache.PeakUsage",
              "java.lang:type=Compilation.TotalCompilationTime",
              "java.lang:type=Memory.HeapMemoryUsage",
              "java.lang:type=MemoryPool,name=Code Cache.Usage",
              "java.lang:type=OperatingSystem.FreePhysicalMemorySize",
              "java.lang:type=OperatingSystem.SystemCpuLoad",
              "java.lang:type=OperatingSystem.CommittedVirtualMemorySize",
              "java.lang:type=MemoryPool,name=Compressed Class Space.PeakUsage",
              "java.lang:type=GarbageCollector,name=PS Scavenge.LastGcInfo",
              "java.lang:type=Runtime.Uptime",
              "java.lang:type=GarbageCollector,name=PS MarkSweep.LastGcInfo",
              "java.lang:type=MemoryPool,name=PS Eden Space.PeakUsage",
              // tabular format is too complex for direct comparison
              "java.lang:type=Runtime.SystemProperties",
              // jolokia mbean server is returned as an actual complex object
              // have no intention of supporting that
              "jolokia:type=MBeanServer.JolokiaMBeanServer",
              // appears to contain a timestamp that differ when running in surefire
              // could be something with several of the tests starting agents etc.
              "JMImplementation:type=MBeanServerDelegate.MBeanServerId"));

  private static Collection<String> UNSAFE_ATTRIBUTES =
      new HashSet<String>(
          Arrays.asList(
              "CollectionUsageThreshold",
              "CollectionUsageThresholdCount",
              "CollectionUsageThresholdExceeded",
              "UsageThreshold",
              "UsageThresholdCount",
              "UsageThresholdExceeded"));

  // Safe values for testing setting attributes
  private static Map<String, Object> ATTRIBUTE_REPLACEMENTS =
      new HashMap<String, Object>() {
        {
          put("jolokia:type=Config.Debug", true);
          put("jolokia:type=Config.HistoryMaxEntries", 20);
          put("jolokia:type=Config.MaxDebugEntries", 50);
          put("java.lang:type=ClassLoading.Verbose", true);
          put("java.lang:type=Threading.ThreadContentionMonitoringEnabled", true);
        }
      };

  @DataProvider
  public static Object[][] nameAndQueryCombinations() {
    return new Object[][] {
      {null, null},
      {RUNTIME, null},
      {null, QUERY},
      {RUNTIME, QUERY}
    };
  }

  @DataProvider
  public static Object[][] safeOperationsToCall() {
    return new Object[][] {
      {
        RemoteJmxAdapter.getObjectName("java.lang:type=Threading"),
        "findDeadlockedThreads",
        new Object[0]
      },
      {RemoteJmxAdapter.getObjectName("java.lang:type=Memory"), "gc", new Object[0]},
      {
        RemoteJmxAdapter.getObjectName("com.sun.management:type=DiagnosticCommand"),
        "vmCommandLine",
        new Object[0]
      },
      {
        RemoteJmxAdapter.getObjectName("com.sun.management:type=HotSpotDiagnostic"),
        "getVMOption",
        new Object[] {"MinHeapFreeRatio"}
      },
      {
        RemoteJmxAdapter.getObjectName("com.sun.management:type=HotSpotDiagnostic"),
        "setVMOption",
        new Object[] {"HeapDumpOnOutOfMemoryError", "true"}
      }
    };
  }

  @DataProvider
  public static Object[][] allNames() {
    final Set<ObjectName> names = ManagementFactory.getPlatformMBeanServer().queryNames(null, null);
    final Object[][] result = new Object[names.size()][1];
    int index = 0;
    for (ObjectName name : names) {
      result[index++][0] = name;
    }
    return result;
  }

  @BeforeClass
  public void startAgent()
      throws MBeanException, ReflectionException, IOException, InstanceAlreadyExistsException,
          NotCompliantMBeanException {

    MBeanServer nativeServer = ManagementFactory.getPlatformMBeanServer();
    // ADD potentially problematic MBeans here (if errors are discovered to uncover other cases that
    // should be managed)
    nativeServer.createMBean(
        MBeanExample.class.getName(),
        RemoteJmxAdapter.getObjectName("jolokia.test:name=MBeanExample"));
    int agentPort;
    JvmAgent.agentmain("port=" + (agentPort = EnvTestUtil.getFreePort()), null);

    final J4pClient connector =
        new J4pClientBuilder().url("http://localhost:" + agentPort + "/jolokia/").build();
    // wait for agent to be running
    await()
        .until(
            Awaitility.matches(
                new ThrowingRunnable() {
                  @Override
                  public void run() throws J4pException {
                    connector.execute(new J4pVersionRequest());
                  }
                }));
    this.adapter = new RemoteJmxAdapter(connector);
  }

  @Test(dataProvider = "nameAndQueryCombinations")
  public void testNames(ObjectName name, QueryExp query) throws IOException {
    final MBeanServerConnection nativeServer = ManagementFactory.getPlatformMBeanServer();
    Assert.assertEquals(nativeServer.queryNames(name, query), this.adapter.queryNames(name, query));
  }

  @Test(dataProvider = "nameAndQueryCombinations")
  public void testInstances(ObjectName name, QueryExp query) throws IOException {
    final MBeanServerConnection nativeServer = ManagementFactory.getPlatformMBeanServer();
    Assert.assertEquals(
        nativeServer.queryMBeans(name, query), this.adapter.queryMBeans(name, query));
  }

  @Test(expectedExceptions = InstanceNotFoundException.class)
  public void testNonExistantMBeanInstance() throws IOException, InstanceNotFoundException {
    this.adapter.getObjectInstance(
        RemoteJmxAdapter.getObjectName("notexistant.domain:type=NonSense"));
  }

  @Test(expectedExceptions = InstanceNotFoundException.class)
  public void testNonExistantMBeanInfo() throws IOException, InstanceNotFoundException {
    this.adapter.getMBeanInfo(RemoteJmxAdapter.getObjectName("notexistant.domain:type=NonSense"));
  }

  @Test(expectedExceptions = RuntimeMBeanException.class)
  public void testFeatureNotSupportedOnServerSide()
      throws IOException, InstanceNotFoundException, MBeanException {
    this.adapter.invoke(
        RemoteJmxAdapter.getObjectName("jolokia.test:name=MBeanExample"),
        "unsupportedOperation",
        new Object[0],
        new String[0]);
  }

  @Test(expectedExceptions = MBeanException.class)
  public void testUnexpectedlyFailingOperation()
      throws IOException, InstanceNotFoundException, MBeanException {
    this.adapter.invoke(
        RemoteJmxAdapter.getObjectName("jolokia.test:name=MBeanExample"),
        "unexpectedFailureMethod",
        new Object[0],
        new String[0]);
  }

  @Test(expectedExceptions = IOException.class)
  public void ensureThatIOExceptionIsChanneledOut() throws IOException {
    new RemoteJmxAdapter(new J4pClientBuilder().url("http://localhost:10/jolokia").build())
        .queryMBeans(null, null);
  }

  @Test(expectedExceptions = AttributeNotFoundException.class)
  public void testGetNonExistantAttribute()
      throws IOException, AttributeNotFoundException, InstanceNotFoundException {
    this.adapter.getAttribute(RUNTIME, "DoesNotExist");
  }

  @Test(expectedExceptions = AttributeNotFoundException.class)
  public void testSetNonExistantAttribute()
      throws IOException, AttributeNotFoundException, InstanceNotFoundException,
          InvalidAttributeValueException {
    this.adapter.setAttribute(RUNTIME, new Attribute("DoesNotExist", false));
  }

  @Test(expectedExceptions = InvalidAttributeValueException.class)
  public void testSetInvalidAttrbuteValue()
      throws IOException, AttributeNotFoundException, InstanceNotFoundException,
          InvalidAttributeValueException {
    this.adapter.setAttribute(
        RemoteJmxAdapter.getObjectName("jolokia:type=Config"),
        new Attribute("HistoryMaxEntries", null));
  }

  @Test
  public void testThatWeAreAbleToInvokeOperationWithOverloadedSignature()
      throws IOException, InstanceNotFoundException, MBeanException {
    // Invoke method that has both primitive and boxed Long as possible input
    this.adapter.invoke(
        RemoteJmxAdapter.getObjectName("java.lang:type=Threading"),
        "getThreadUserTime",
        new Object[] {1L},
        new String[] {"long"});
    this.adapter.invoke(
        RemoteJmxAdapter.getObjectName("java.lang:type=Threading"),
        "getThreadUserTime",
        new Object[] {new long[] {1L}},
        new String[] {"[J"});
  }

  @Test(dataProvider = "safeOperationsToCall")
  public void testInvoke(ObjectName name, String operation, Object[] arguments)
      throws IOException, ReflectionException, MBeanException {
    final MBeanServerConnection nativeServer = ManagementFactory.getPlatformMBeanServer();
    try {
      for (MBeanOperationInfo operationInfo : this.adapter.getMBeanInfo(name).getOperations()) {
        if (operationInfo.getName().equals(operation)
                && operationInfo.getSignature().length == arguments.length) {
          String[] signature = new String[operationInfo.getSignature().length];
          for (int i = 0; i < signature.length; i++) {
            signature[i] = operationInfo.getSignature()[i].getType();
          }
          Assert.assertEquals(
                  this.adapter.invoke(name, operation, arguments, signature),
                  nativeServer.invoke(name, operation, arguments, signature));
        }
      }
    } catch (InstanceNotFoundException e) {
      System.out.println(name + " not found in JVM " + System.getProperty("java.runtime.name") + " " + System.getProperty("java.runtime.version") +" skipping");
    }
  }

  @Test(dataProvider = "allNames")
  public void testInstances(ObjectName name) throws InstanceNotFoundException, IOException {
    final MBeanServerConnection nativeServer = ManagementFactory.getPlatformMBeanServer();
    final ObjectInstance nativeInstance = nativeServer.getObjectInstance(name);
    final ObjectInstance jolokiaInstance = this.adapter.getObjectInstance(name);
    Assert.assertEquals(jolokiaInstance, nativeInstance);

    Assert.assertEquals(
        nativeServer.isInstanceOf(jolokiaInstance.getObjectName(), jolokiaInstance.getClassName()),
        this.adapter.isInstanceOf(jolokiaInstance.getObjectName(), jolokiaInstance.getClassName()));

    Assert.assertEquals(nativeServer.isRegistered(name), this.adapter.isRegistered(name));

    try {
      final Class<?> klass = Class.forName(jolokiaInstance.getClassName());
      // check that inheritance works the same for both interfaces
      if (klass.getSuperclass() != null) {
        Assert.assertEquals(
            nativeServer.isInstanceOf(
                jolokiaInstance.getObjectName(), klass.getSuperclass().toString()),
            this.adapter.isInstanceOf(
                jolokiaInstance.getObjectName(), klass.getSuperclass().toString()));
        if (klass.getInterfaces().length > 0) {
          Assert.assertEquals(
              nativeServer.isInstanceOf(
                  jolokiaInstance.getObjectName(), klass.getInterfaces()[0].toString()),
              this.adapter.isInstanceOf(
                  jolokiaInstance.getObjectName(), klass.getInterfaces()[0].toString()));
        }
      }
    } catch (ClassNotFoundException ignore) {
    }
  }

  @Test(dataProvider = "allNames")
  public void testMBeanInfo(ObjectName name)
      throws IntrospectionException, ReflectionException, InstanceNotFoundException, IOException,
          AttributeNotFoundException, MBeanException, InvalidAttributeValueException {
    final MBeanServerConnection nativeServer = ManagementFactory.getPlatformMBeanServer();
    final MBeanInfo jolokiaMBeanInfo = this.adapter.getMBeanInfo(name);
    final MBeanInfo nativeMBeanInfo = nativeServer.getMBeanInfo(name);
    Assert.assertEquals(jolokiaMBeanInfo.getDescription(), nativeMBeanInfo.getDescription());
    Assert.assertEquals(jolokiaMBeanInfo.getClassName(), nativeMBeanInfo.getClassName());
    Assert.assertEquals(
        jolokiaMBeanInfo.getAttributes().length, nativeMBeanInfo.getAttributes().length);
    Assert.assertEquals(
        jolokiaMBeanInfo.getOperations().length, nativeMBeanInfo.getOperations().length);

    final AttributeList replacementValues = new AttributeList();
    final AttributeList originalValues = new AttributeList();

    for (MBeanAttributeInfo attribute : jolokiaMBeanInfo.getAttributes()) {
      final String qualifiedName = name + "." + attribute.getName();
      if (UNSAFE_ATTRIBUTES.contains(attribute.getName())) { // skip known failing attributes
        continue;
      }
      if (ATTRIBUTES_NOT_SAFE_FOR_DIRECT_COMPARISON.contains(qualifiedName)) {
        this.adapter.getAttribute(name, attribute.getName());
        continue;
      }
      final Object jolokiaAttributeValue = this.adapter.getAttribute(name, attribute.getName());
      final Object nativeAttributeValue = nativeServer.getAttribute(name, attribute.getName());
      // data type probably not so important (ie. long vs integer), as long as value is close enough
      if (jolokiaAttributeValue instanceof Number) {
        Assert.assertEquals(
            ((Number) jolokiaAttributeValue).doubleValue(),
            ((Number) nativeAttributeValue).doubleValue(),
            0.1,
            "Attribute mismatch: " + qualifiedName);
      } else {
        Assert.assertEquals(
            jolokiaAttributeValue, nativeAttributeValue, "Attribute mismatch: " + qualifiedName);
      }
      if (attribute.isWritable()) {
        final Object newValue = ATTRIBUTE_REPLACEMENTS.get(qualifiedName);

        if (newValue != null) {
          final Attribute newAttribute = new Attribute(attribute.getName(), newValue);
          replacementValues.add(newAttribute);
          this.adapter.setAttribute(name, newAttribute);
          // use native connection and verify that attribute is now new value
          Assert.assertEquals(nativeServer.getAttribute(name, attribute.getName()), newValue);
          // restore original value
          final Attribute restoreAttribute =
              new Attribute(attribute.getName(), nativeAttributeValue);
          this.adapter.setAttribute(name, restoreAttribute);
          originalValues.add(restoreAttribute);
          // now do multi argument setting
          this.adapter.setAttributes(name, replacementValues);
          Assert.assertEquals(nativeServer.getAttribute(name, attribute.getName()), newValue);
          // and restore
          this.adapter.setAttributes(name, originalValues);
        }
      }
    }
  }

  @Test
  public void verifyUnsupportedFunctions() {
    // ensure that methods give the expected exception and nothing else
    try {
      this.adapter.createMBean("java.lang.Object", RUNTIME);
      Assert.fail("Operation should not be supported by adapter");
    } catch (UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.createMBean("java.lang.Object", RUNTIME, RUNTIME);
      Assert.fail("Operation should not be supported by adapter");
    } catch (UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.createMBean("java.lang.Object", RUNTIME, new Object[0], new String[0]);
      Assert.fail("Operation should not be supported by adapter");
    } catch (UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.createMBean("java.lang.Object", RUNTIME, RUNTIME, new Object[0], new String[0]);
      Assert.fail("Operation should not be supported by adapter");
    } catch (UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.unregisterMBean(RUNTIME);
      Assert.fail("Operation should not be supported by adapter");
    } catch (UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.addNotificationListener(RUNTIME, (NotificationListener) null, null, null);
      Assert.fail("Operation should not be supported by adapter");
    } catch (UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.addNotificationListener(RUNTIME, RUNTIME, null, null);
      Assert.fail("Operation should not be supported by adapter");
    } catch (UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.removeNotificationListener(RUNTIME, RUNTIME);
      Assert.fail("Operation should not be supported by adapter");
    } catch (UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.removeNotificationListener(RUNTIME, RUNTIME, null, null);
      Assert.fail("Operation should not be supported by adapter");
    } catch (UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.removeNotificationListener(RUNTIME, (NotificationListener) null);
      Assert.fail("Operation should not be supported by adapter");
    } catch (UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.removeNotificationListener(RUNTIME, (NotificationListener) null, null, null);
      Assert.fail("Operation should not be supported by adapter");
    } catch (UnsupportedOperationException ignore) {
    }
  }

  @Test
  public void testOverallOperations() throws IOException {
    final MBeanServerConnection nativeServer = ManagementFactory.getPlatformMBeanServer();
    Assert.assertEquals(
        this.adapter.getMBeanCount(),
        nativeServer.getMBeanCount(),
        "Number of MBeans are the same");

    final String[] jolokiaDomains = this.adapter.getDomains();
    Arrays.sort(jolokiaDomains);
    String[] nativeDomains = nativeServer.getDomains();
    Arrays.sort(nativeDomains);
    Assert.assertEquals(
            jolokiaDomains,
            nativeDomains,
        "Domain list is the same");

    Assert.assertEquals(
        this.adapter.getDefaultDomain(), nativeServer.getDefaultDomain(), "Default domain");
  }

  @AfterClass
  public void stopAgent() {
    JvmAgent.agentmain("mode=stop", null);
  }
}
