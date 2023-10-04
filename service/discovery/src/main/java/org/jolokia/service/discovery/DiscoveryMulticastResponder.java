package org.jolokia.service.discovery;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

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
    private final List<MulticastSocketListenerThread> listenerThreads;

    /**
     * Create the responder which can be started and stopped
     */
    public DiscoveryMulticastResponder() {
        super(Init.class, 0 /* no order required */);
        listenerThreads = new ArrayList<>();
    }

    /**
     * Start the responder (if not already started)
     */
    @Override
    public void init(JolokiaContext pContext) {
        if (discoveryEnabled(pContext) && listenerThreads.isEmpty()) {
            List<InetAddress> addresses = NetworkUtil.getMulticastAddresses();
            if (addresses.isEmpty()) {
                pContext.info("No suitable address found for listening on multicast discovery requests");
                return;
            }
            for (InetAddress addr : addresses) {
                try {
                    MulticastSocketListenerThread thread = new MulticastSocketListenerThread("JolokiaDiscoveryListenerThread-" + addr.getHostAddress(), addr, pContext);
                    thread.start();
                    listenerThreads.add(thread);
                    // One thread might be enough ?
                    //break;
                } catch (IOException e) {
                    pContext.error("Cannot start multicast discovery listener thread on " + addr + ": " + e, e);
                }
            }
            if (listenerThreads.isEmpty()) {
                pContext.info("Cannot start a single multicast discovery listener");
            }
        }
    }

    /**
     * Stop the responder (if not already stopped). Can be restarted afterwards.
     */
    @Override
    public synchronized void destroy() {
        if (!listenerThreads.isEmpty()) {
            for (MulticastSocketListenerThread thread : listenerThreads) {
                thread.shutdown();
            }
        }
        listenerThreads.clear();
    }

    // Check whether discovery is enabled throught the config
    private boolean discoveryEnabled(JolokiaContext pJolokiaContext) {
        return (pJolokiaContext.getConfig(ConfigKey.DISCOVERY_ENABLED) != null &&
                Boolean.parseBoolean(pJolokiaContext.getConfig(ConfigKey.DISCOVERY_ENABLED))) ||
               pJolokiaContext.getConfig(ConfigKey.DISCOVERY_AGENT_URL) != null;
    }
}
