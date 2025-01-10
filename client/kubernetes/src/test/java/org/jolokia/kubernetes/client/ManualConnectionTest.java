package org.jolokia.kubernetes.client;

import java.io.IOException;
import java.util.Collections;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

/**
 * This is a utility to attempt to connect to manually test connection to a JVM running in kubernetes
 *  1. Ensure that your current kubernetes context is set to the context you attempt to use
 *     (e.g. kubectl config use-context my-context)
 *  2. Ensure that your kubernetes config contains valid credentials for the k8s cluster of that context
 */
public class ManualConnectionTest {

	@Test(groups = "manual")
	@Ignore
	public void testConnect() throws IOException {
		final JMXServiceURL jmxServiceURL = new JMXServiceURL(
				"service:jmx:kubernetes:///jfr/petclinic-.+-.+:8778/jolokia/");

		try (final JMXConnector connector = JMXConnectorFactory.connect(jmxServiceURL,
				Collections.singletonMap(JMXConnector.CREDENTIALS, new String[] { "user", "password" }))) {
			connector.getConnectionId();
			connector.connect();
			connector.getMBeanServerConnection().getMBeanCount();
		}
	}

}
