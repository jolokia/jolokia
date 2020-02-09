package org.jolokia.client;

import static com.jayway.awaitility.Awaitility.await;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.core.ThrowingRunnable;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Set;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.request.J4pVersionRequest;
import org.jolokia.jvmagent.JvmAgent;
import org.jolokia.test.util.EnvTestUtil;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * I test the Jolokia Jmx adapter by comparing results with a traditional
 * MBeanConnection
 */
public class JmxBridgeTest {

  private int agentPort;
  private RemoteJmxAdapter adapter;

  private final static ObjectName RUNTIME = getObjectName("java.lang:type=Runtime");
  private final static QueryExp QUERY = Query.or(Query.anySubString(Query.classattr(), Query.value("Object")), Query.anySubString(Query.classattr(), Query.value("String")));

  private static ObjectName getObjectName(String s) {
    try {
      return ObjectName.getInstance(s);
    } catch (MalformedObjectNameException e) {
      Assert.fail("Invalid object name " + s);
      return null;
    }
  }

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
  public static Object[][] allNames() {
    final Set<ObjectName> names = ManagementFactory.getPlatformMBeanServer()
        .queryNames(null, null);
    final Object[][] result = new Object[names.size()][1];
    int index=0;
    for(ObjectName name : names) {
      result[index++][0] = name;
    }
    return result;
  }

  @BeforeClass
  public void startAgent()
      throws MalformedObjectNameException, AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException, IOException {
    final String vmName = (String) ManagementFactory.getPlatformMBeanServer()
        .getAttribute(new ObjectName("java.lang:type=Runtime"), "Name");
    final String pid = vmName.substring(0, vmName.indexOf('@'));

    JvmAgent.agentmain("port=" + (this.agentPort = EnvTestUtil.getFreePort()), null);

    final J4pClient connector = new J4pClientBuilder()
        .url("http://localhost:" + this.agentPort + "/jolokia")
        .build();

    //wait for agent to be running
    await().until(Awaitility.matches(new ThrowingRunnable() {
      @Override
      public void run() throws J4pException {
        connector.execute(new J4pVersionRequest());
      }
    }));
    this.adapter = new RemoteJmxAdapter(
        connector);
  }

  @Test(dataProvider = "nameAndQueryCombinations")
  public void testNames(ObjectName name, QueryExp query) throws IOException {
    final MBeanServerConnection nativeServer = ManagementFactory.getPlatformMBeanServer();
    Assert.assertEquals(
        nativeServer.queryNames(name, query),
        this.adapter.queryNames(name, query));
  }

  @Test(dataProvider = "nameAndQueryCombinations")
  public void testInstances(ObjectName name, QueryExp query) throws IOException {
    final MBeanServerConnection nativeServer = ManagementFactory.getPlatformMBeanServer();
    Assert.assertEquals(
        nativeServer.queryMBeans(name, query),
        this.adapter.queryMBeans(name, query));
  }

  @Test(dataProvider = "allNames")
  public void testInstances(ObjectName name) throws InstanceNotFoundException, IOException {
    final MBeanServerConnection nativeServer = ManagementFactory.getPlatformMBeanServer();
    final ObjectInstance nativeInstance = nativeServer.getObjectInstance(name);
    final ObjectInstance jolokiaInstance = this.adapter.getObjectInstance(name);
    Assert.assertEquals(
        jolokiaInstance,
        nativeInstance
    );

    Assert.assertEquals(
        nativeServer.isInstanceOf(jolokiaInstance.getObjectName(), jolokiaInstance.getClassName()),
        this.adapter.isInstanceOf(jolokiaInstance.getObjectName(), jolokiaInstance.getClassName()));

    Assert.assertEquals(
        nativeServer.isRegistered(name),
        this.adapter.isRegistered(name)
    );

    try {
      final Class<?> klass = Class.forName(jolokiaInstance.getClassName());
      //check that inheritance works the same for both interfaces
      if(klass.getSuperclass() != null) {
        Assert.assertEquals(
            nativeServer.isInstanceOf(jolokiaInstance.getObjectName(), klass.getSuperclass().toString()),
            this.adapter.isInstanceOf(jolokiaInstance.getObjectName(), klass.getSuperclass().toString())
        );
        if(klass.getInterfaces().length > 0) {
          Assert.assertEquals(
              nativeServer.isInstanceOf(jolokiaInstance.getObjectName(), klass.getInterfaces()[0].toString()),
              this.adapter.isInstanceOf(jolokiaInstance.getObjectName(), klass.getInterfaces()[0].toString())
          );

        }
      }
    } catch (ClassNotFoundException ignore) {
    }

  }

//  @Test(dataProvider = "allNames")
  public void testMBeanInfo(ObjectName name)
      throws IntrospectionException, ReflectionException, InstanceNotFoundException, IOException {
    final MBeanServerConnection nativeServer = ManagementFactory.getPlatformMBeanServer();
    Assert.assertEquals(
        this.adapter.getMBeanInfo(name),
        nativeServer.getMBeanInfo(name));
  }

  @Test
  public void verifyUnsupportedFunctions()
      throws IOException, InstanceNotFoundException, ListenerNotFoundException {
    //ensure that methods give the expected exception and nothing else
    try {
      this.adapter.createMBean("java.lang.Object", RUNTIME);
      Assert.fail("Operation should not be supported by adapter");
    } catch ( UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.createMBean("java.lang.Object", RUNTIME, RUNTIME);
      Assert.fail("Operation should not be supported by adapter");
    } catch ( UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.createMBean("java.lang.Object", RUNTIME, new Object[0], new String[0]);
      Assert.fail("Operation should not be supported by adapter");
    } catch ( UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.createMBean("java.lang.Object", RUNTIME, RUNTIME, new Object[0], new String[0]);
      Assert.fail("Operation should not be supported by adapter");
    } catch ( UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.unregisterMBean(RUNTIME);
      Assert.fail("Operation should not be supported by adapter");
    } catch ( UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.addNotificationListener(RUNTIME, (NotificationListener)null, null, null);
      Assert.fail("Operation should not be supported by adapter");
    } catch ( UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.addNotificationListener(RUNTIME, RUNTIME, null, null);
      Assert.fail("Operation should not be supported by adapter");
    } catch ( UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.removeNotificationListener(RUNTIME, RUNTIME);
      Assert.fail("Operation should not be supported by adapter");
    } catch ( UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.removeNotificationListener(RUNTIME, RUNTIME, null, null);
      Assert.fail("Operation should not be supported by adapter");
    } catch ( UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.removeNotificationListener(RUNTIME, (NotificationListener)null);
      Assert.fail("Operation should not be supported by adapter");
    } catch ( UnsupportedOperationException ignore) {
    }
    try {
      this.adapter.removeNotificationListener(RUNTIME, (NotificationListener)null, null, null);
      Assert.fail("Operation should not be supported by adapter");
    } catch ( UnsupportedOperationException ignore) {
    }
  }

  @Test
  public void testOverallOperations() throws IOException {
    final MBeanServerConnection nativeServer = ManagementFactory.getPlatformMBeanServer();
    Assert.assertEquals(
        this.adapter.getMBeanCount(),
        nativeServer.getMBeanCount(),
        "Number of MBeans are the same");

    Assert.assertEquals(
        this.adapter.getDomains(),
        nativeServer.getDomains(),
        "Domain list is the same"
    );

    Assert.assertEquals(
        this.adapter.getDefaultDomain(),
        nativeServer.getDefaultDomain(),
        "Default domain"
    );

  }

  @AfterClass
  public void stopAgent() {
    JvmAgent.agentmain("mode=stop", null);
  }
}
