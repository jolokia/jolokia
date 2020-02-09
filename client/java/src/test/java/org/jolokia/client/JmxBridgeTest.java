package org.jolokia.client;

import static com.jayway.awaitility.Awaitility.await;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.core.ThrowingRunnable;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
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
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
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
    final MBeanServer nativeServer = ManagementFactory.getPlatformMBeanServer();
    Assert.assertEquals(
        nativeServer.queryNames(name, query),
        this.adapter.queryNames(name, query));
  }

  @AfterClass
  public void stopAgent() {
    JvmAgent.agentmain("mode=stop", null);
  }
}
