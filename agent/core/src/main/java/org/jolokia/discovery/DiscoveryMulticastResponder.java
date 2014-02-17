package org.jolokia.discovery;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import org.jolokia.service.JolokiaContext;
import org.jolokia.util.NetworkUtil;

/**
 * A receiver which binds to a multicast sockets and responds to multicast requests.
 * It has lifecycle method for starting and stopping the discovery mechanism.
 *
 * @author roland
 * @since 24.01.14
 */
public class DiscoveryMulticastResponder {

    // Entry point in the jolokia core world
    private final JolokiaContext context;

    // host address to listen to
    private InetAddress hostAddress;

    // Listener threads responsible for creating the response as soon as a discovery request
    // arrives.
    private List<MulticastSocketListenerThread> listenerThreads;

    /**
     * Create the responder which can be started and stopped and which detects the address to listen on on its own.
     *
     * @param pContext the central jolokia context
     * @throws IOException when the host is not known.
     */
    public DiscoveryMulticastResponder(JolokiaContext pContext) throws UnknownHostException {
        this(null, pContext);
    }

    /**
     * Create the responder which can be started and stopped
     *
     * @param pHostAddress host address from which the binding is performed
     * @param pContext the central jolokia context
     */
    public DiscoveryMulticastResponder(InetAddress pHostAddress,
                                       JolokiaContext pContext) {
        hostAddress = pHostAddress;
        context = pContext;
        listenerThreads = new ArrayList<MulticastSocketListenerThread>();
    }

    /**
     * Start the responder (if not already started)
     */
    public synchronized void start() throws IOException {
        if (listenerThreads.size() == 0) {
            List<InetAddress> addresses = hostAddress == null ? NetworkUtil.getMulticastAddresses() : Arrays.asList(hostAddress);
            if (addresses.size() == 0) {
                context.info("No suitable address found for listening on multicast discovery requests");
                return;
            }
            for (InetAddress addr : addresses) {
                MulticastSocketListenerThread thread = new MulticastSocketListenerThread(addr,context);
                thread.start();
                listenerThreads.add(thread);
                // One thread is enough for now.
                //break;
            }
        }
    }

    /**
     * Stop the responder (if not already stopped). Can be restarted aftewards.
     */
    public synchronized void stop() {
        if (listenerThreads.size() > 0) {
            for (MulticastSocketListenerThread thread : listenerThreads) {
                thread.shutdown();
            }
        }

        listenerThreads.clear();
    }
}
