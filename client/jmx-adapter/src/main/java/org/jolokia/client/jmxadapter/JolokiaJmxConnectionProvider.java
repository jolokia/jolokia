/*
 * Copyright 2009-2025 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jolokia.client.jmxadapter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorProvider;
import javax.management.remote.JMXServiceURL;

/**
 * <p>{@link JMXConnectorProvider} implementation for handling JMX URLs using the Jolokia protocol</p>
 * <p>Syntax: {@code service:jmx:jolokia[+http[s]]://host:port/path/to/jolokia/with/slash/suffix/} - this is compliant
 * with <a href="https://www.ietf.org/rfc/rfc2609.html#section-2.1">RFC 2609</a></p>
 *
 * Example:
 * <pre>{@code
 *   //NB: include trailing slash
 *   https will be used if port number fits the pattern *443 or connect env map contains "jmx.remote.x.check.stub"->"true"
 *   JMXConnector connector = JMXConnectorFactory
 *             .connect(new JMXServiceURL("service:jmx:jolokia://host:port/jolokia/"), Collections.singletonMap(JMXConnector.CREDENTIALS, Arrays
 *             .asList("user", "password")));
 *         connector.connect();
 *         connector.getMBeanServerConnection();
 *
 * }</pre>
 */
public class JolokiaJmxConnectionProvider implements JMXConnectorProvider {

    @Override
    public JMXConnector newJMXConnector(JMXServiceURL serviceURL, Map<String, ?> environment) throws IOException {
        // the exception will be handled by javax.management.remote.JMXConnectorFactory so that other handlers are
        // allowed to handle other protocols
        if (!serviceURL.getProtocol().startsWith("jolokia")) {
            throw new MalformedURLException(String.format("Invalid URL %s : Only protocol \"jolokia[+http[s]]\" is supported (not %s)", serviceURL, serviceURL.getProtocol()));
        }
        return new JolokiaJmxConnector(serviceURL, environment);
    }

}
