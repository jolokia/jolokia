package org.jolokia.kubernetes.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.Options;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Test that JMX connections done with KubernetesJmxConnectionProvider are
 * functional. In order to be able to test this in a contained environment, the
 * kubernetes API is mocked with wiremock.
 */
public class KubernetesTest {

  private WireMockServer wireMockServer;
  private MBeanServerConnection jolokiaConnection;

  @BeforeTest
  public void setUp() throws Exception {
    wireMockServer = new WireMockServer(Options.DYNAMIC_PORT);
    wireMockServer.start();
    // Ensure we get a fresh config
    KubernetesJmxConnector.resetKubernetesConfig();

    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<byte[]> configResponse;
    try {
      configResponse = client.send(
          HttpRequest.newBuilder(URI.create(wireMockServer.baseUrl() + "/mock-kube-config.yml")).GET().build(),
          HttpResponse.BodyHandlers.ofByteArray());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(e);
    }
    Assert.assertEquals(configResponse.statusCode(), 200);
    final File configFile = File.createTempFile("mock-kube-config", ".yml");
    Files.write(configFile.toPath(), configResponse.body());
    // Setting taken from:
    // https://github.com/fabric8io/kubernetes-client/blob/77a65f7d40f31a5dc37492cd9de3c317c2702fb4/kubernetes-client-api/src/main/java/io/fabric8/kubernetes/client/Config.java#L120,
    // unlikely to change
    System.setProperty("kubeconfig", configFile.getAbsolutePath());
    Assert.assertNotNull(jolokiaConnection = getKubernetesMBeanConnector());
  }

  static final String jolokiaUrl = "service:jmx:kubernetes:///ns1/pod-abcdef/jolokia";

  @Test
  public void testExecuteOperation() throws InstanceNotFoundException, MalformedObjectNameException, MBeanException,
      ReflectionException, IOException {
    jolokiaConnection.invoke(new ObjectName("java.lang:type=Memory"), "gc", new Object[0], new String[0]);
  }

  @Test
  public void testReadAttribute() throws InstanceNotFoundException, AttributeNotFoundException,
      MalformedObjectNameException, MBeanException, ReflectionException, IOException {
    MBeanServerConnection jmxConnection = jolokiaConnection;
    assertOneSingleAttribute(jmxConnection);

  }

  private void assertOneSingleAttribute(MBeanServerConnection jmxConnection) throws MalformedObjectNameException,
      MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException, IOException {
    ObjectName objectName = new ObjectName("java.lang:type=Memory");
    String attribute = "Verbose";
    Assert.assertEquals(false, jmxConnection.getAttribute(objectName, attribute));
  }

  private static MBeanServerConnection getKubernetesMBeanConnector() throws IOException {
    Map<String, Object> environment = new HashMap<>();
    environment.put(JMXConnector.CREDENTIALS, new String[] { "admin", "secret" });
    environment.put(KubernetesJmxConnector.KUBERNETES_CLIENT_CONTEXT, "test");

    final JMXConnector connector = new KubernetesJmxConnectionProvider().newJMXConnector(
      new JMXServiceURL(jolokiaUrl), environment);
    connector.connect();
    return connector
        .getMBeanServerConnection();
  }

  @AfterTest
  public void after() {
    // To aid debugging if there is a problem with matching requests to wiremock
    wireMockServer.findAllUnmatchedRequests()
        .forEach(req -> System.out.append("Unmatched wiremock request:\n").println(req.toString()));
    wireMockServer.findNearMissesForAllUnmatchedRequests()
        .forEach(miss -> System.out.append("Near miss in wiremock mappings:\n").println(miss));
  }

}
