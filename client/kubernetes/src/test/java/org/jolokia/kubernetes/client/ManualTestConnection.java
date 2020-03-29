package org.jolokia.kubernetes.client;

import java.io.IOException;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import org.testng.annotations.Test;


public class ManualTestConnection {

  @Test(groups = "manual")
  public void testConnect() throws IOException {
    final JMXServiceURL jmxServiceURL = new JMXServiceURL(
        "service:jmx:kubernetes:///api/v1/namespaces/che/pods/workspace.+tools-.+-.+/proxy/manage/jolokia/"
        );
    jmxServiceURL.getProtocol();

    JMXConnectorFactory.connect(jmxServiceURL).getConnectionId();

  }

}
