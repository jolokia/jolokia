package org.jolokia.client.jmxadapter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorProvider;
import javax.management.remote.JMXServiceURL;

/**
 * I provide support for handling JMX urls for the Jolokia protocol
 * Syntax service:jmx:jolokia://host:port/path/to/jolokia/with/slash/suffix/
 * My Jar contains a service loader, so that Jolokia JMX protocol is supported
 * as long as my jar (jmx-adapter-version-standalone.jar) is on the classpath
 *
 * <pre>
 *   Example:
 *   //NB: include trailing slash
 *   https will be used if port number fits the pattern *443 or connect env map contains "jmx.remote.x.check.stub"->"true"
 *   JMXConnector connector = JMXConnectorFactory
 *             .connect(new JMXServiceURL("service:jmx:jolokia://host:port/jolokia/"), Collections.singletonMap(JMXConnector.CREDENTIALS, Arrays
 *             .asList("user", "password")));
 *         connector.connect();
 *         connector.getMBeanServerConnection();
 *
 * </pre>
 */
public class JolokiaJmxConnectionProvider implements JMXConnectorProvider {
    @Override
    public JMXConnector newJMXConnector(JMXServiceURL serviceURL, Map<String, ?> environment) throws IOException {
        //the exception will be handled by JMXConnectorFactory so that other handlers are allowed to handle
        //other protocols
        if(!"jolokia".equals(serviceURL.getProtocol())) {
            throw new MalformedURLException(String.format("Invalid URL %s : Only protocol \"jolokia\" is supported (not %s)",  serviceURL, serviceURL.getProtocol()));
        }
        return new JolokiaJmxConnector(serviceURL, environment);
    }
}
