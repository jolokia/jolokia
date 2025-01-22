package org.jolokia.service.discovery;

import java.io.IOException;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.service.api.*;
import org.jolokia.server.core.util.NetworkUtil;

/**
 * A receiver which binds to a multicast sockets and responds to multicast requests.
 * It has lifecycle method for starting and stopping the discovery mechanism.
 *
 * @author roland
 * @since 24.01.14
 */
public class DiscoveryMulticastResponder extends AbstractJolokiaService<JolokiaService.Init> implements JolokiaService.Init {

    // Listener threads responsible for creating the response as soon as a discovery request
    // arrives.
    private MulticastSocketListenerThread listenerThread;

    /**
     * Create the responder which can be started and stopped
     */
    public DiscoveryMulticastResponder() {
        super(Init.class, 0 /* no order required */);
    }

    /**
     * Start the responder (if not already started)
     */
    @Override
    public void init(JolokiaContext pContext) {
        if (discoveryEnabled(pContext) && listenerThread == null) {
            // The listener uses _single_ (see jolokia/jolokia#620) UDP responder thread.
            // We don't need to listen on many sockets - just one _any_ (0.0.0.0 or ::) socket
            // MulticastSocket constructor with port only calls java.net.InetAddress.anyLocalAddress

            if (!NetworkUtil.isMulticastSupported()) {
                pContext.info("Multicast is not supported");
                return;
            }

            String multicastBindAddress = pContext.getConfig(ConfigKey.MULTICAST_BIND_ADDRESS);

            try {
                pContext.debug("Creating MulticastSocketListenerThread for address " + multicastBindAddress);
                MulticastSocketListenerThread thread = new MulticastSocketListenerThread(multicastBindAddress, pContext);
                thread.start();
                listenerThread = thread;
            } catch (IOException e) {
                pContext.error("Cannot start multicast discovery listener thread on " + multicastBindAddress + ": " + e, e);
            }
        }
    }

    /**
     * Stop the responder (if not already stopped). Can be restarted afterwards.
     */
    @Override
    public synchronized void destroy() {
        if (listenerThread != null) {
            listenerThread.shutdown();
            listenerThread = null;
        }
    }

    // Check whether discovery is enabled through the config
    private boolean discoveryEnabled(JolokiaContext pJolokiaContext) {
        return (pJolokiaContext.getConfig(ConfigKey.DISCOVERY_ENABLED) != null &&
                Boolean.parseBoolean(pJolokiaContext.getConfig(ConfigKey.DISCOVERY_ENABLED))) ||
               pJolokiaContext.getConfig(ConfigKey.DISCOVERY_AGENT_URL) != null;
    }
}
