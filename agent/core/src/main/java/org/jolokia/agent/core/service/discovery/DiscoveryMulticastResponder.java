package org.jolokia.agent.core.service.discovery;

import org.jolokia.agent.core.service.JolokiaService;

/**
 * Interface for starting and stopping a multicast responder.
 * It can be looked up and take part in the Jolokia lifecycle
 *
 * @author roland
 * @since 25.02.14
 */
public interface DiscoveryMulticastResponder extends JolokiaService<DiscoveryMulticastResponder> {
}
