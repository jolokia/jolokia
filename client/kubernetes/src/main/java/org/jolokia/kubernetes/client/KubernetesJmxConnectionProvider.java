package org.jolokia.kubernetes.client;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorProvider;
import javax.management.remote.JMXServiceURL;
import org.jolokia.client.jmxadapter.JolokiaJmxConnector;

/**
 * I provide support for handling JMX urls for the Jolokia protocol
 * Syntax service:jmx:jolokia://host:port/path/to/jolokia/with/slash/suffix/
 * My Jar contains a service loader, so that Jolokia JMX protocol is supported
 * as long as my jar (jmx-adapter-version-standalone.jar) is on the classpath
 *
 * <code>
 *   Example:
 *   //NB: include trailing slash
 *   https will be used if port number fits the pattern *443 or connect env map contains "jmx.remote.x.check.stub"->"true"
 *   JMXConnector connector = JMXConnectorFactory
 *             .connect(new JMXServiceURL("service:jmx:kubernetes://host:port/jolokia/"), Collections.singletonMap(JMXConnector.CREDENTIALS, Arrays
 *             .asList("user", "password")));
 *         connector.connect();
 *         connector.getMBeanServerConnection();
 *
 * </code>
 */
public class KubernetesJmxConnectionProvider implements JMXConnectorProvider {
    @Override
    public JMXConnector newJMXConnector(JMXServiceURL serviceURL, Map<String, ?> environment) throws IOException {
        //the exception will be handled by JMXConnectorFactory so that other handlers are allowed to handle
        //other protocols
        if(!"kubernetes".equals(serviceURL.getProtocol())) {
            throw new MalformedURLException("I only serve Kubernetes connections");
        }
        return new KubernetesJmxConnector(serviceURL, environment);
    }
}
