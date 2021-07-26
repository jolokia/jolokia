package org.jolokia.kubernetes.client;

import java.io.IOException;
import java.util.Collections;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import org.testng.annotations.Test;


public class ManualTestConnection {

  @Test(groups = "manual")
  public void testConnect() throws IOException {
    final JMXServiceURL jmxServiceURL = new JMXServiceURL(
        "service:jmx:kubernetes:///che/petclinic-.+-.+:8778/jolokia/"
        );
    jmxServiceURL.getProtocol();

    final JMXConnector connector = JMXConnectorFactory.connect(jmxServiceURL,
        Collections.singletonMap(JMXConnector.CREDENTIALS, new String[]{"user", "password"}));
    connector.getConnectionId();
    connector.connect();
    connector.getMBeanServerConnection().getMBeanCount();

  }

}
