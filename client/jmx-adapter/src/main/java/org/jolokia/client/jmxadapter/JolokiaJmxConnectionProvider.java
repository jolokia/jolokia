package org.jolokia.client.jmxadapter;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorProvider;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.Map;

public class JolokiaJmxConnectionProvider implements JMXConnectorProvider {
    @Override
    public JMXConnector newJMXConnector(JMXServiceURL serviceURL, Map<String, ?> environment) throws IOException {
        return new JolokiaJmxConnector(serviceURL, environment);
    }
}
