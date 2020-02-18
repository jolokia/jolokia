package org.jolokia.client.jmxadapter;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorProvider;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

/**
 * I provide support for handling JMX urls for the Jolokia protocol
 * Syntax service:jmx:jolokia://host:port/path/to/jolokia/with/slash/suffix/
 * My Jar contains a service loader, so that Jolokia JMX protocol is supported
 * as long as my jar is on the classpath
 */
public class JolokiaJmxConnectionProvider implements JMXConnectorProvider {
    @Override
    public JMXConnector newJMXConnector(JMXServiceURL serviceURL, Map<String, ?> environment) throws IOException {
        //the exception will be handled by JMXConnectorFactory so that other handlers are allowed to handle
        //other protocols
        if(!"jolokia".equals(serviceURL.getProtocol())) {
            throw new MalformedURLException("I only serve Jolokia connections");
        }
        return new JolokiaJmxConnector(serviceURL, environment);
    }
}
