package org.jolokia.discovery;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.LogHandler;
import org.jolokia.util.NetworkUtil;

/**
 * A receiver which binds to a multicast sockets and responds to multicast requests.
 * It has lifecycle method for starting and stopping the discovery mechanism.
 *
 * @author roland
 * @since 24.01.14
 */
public class DiscoveryMulticastResponder {

    private final AgentDetailsHolder detailsHolder;
    private final Restrictor restrictor;
    private final String multicastGroup;
    private final int multicastPort;
    private final LogHandler logHandler;

    private InetAddress hostAddress;

    // Listener threads responsible for creating the response as soon as a discovery request
    // arrives.
    private List<MulticastSocketListenerThread> listenerThreads;

    /**
     * Create the responder which can be started and stopped and which detects the address to listen on on its own.
     *
     * @param pDetailsHolder holds the details for an agent
     * @param pRestrictor restrictor used for avoiding responding to sites which are not allowed to connect
     * @param pMulticastGroup multicast IPv4 address
     * @param pMulticastPort multicast port
     * @param pLogHandler used for logging and debugging
     * @throws UnknownHostException when the host is not known.
     */
    public DiscoveryMulticastResponder(AgentDetailsHolder pDetailsHolder,
                                       Restrictor pRestrictor,
                                       String pMulticastGroup,
                                       int pMulticastPort,
                                       LogHandler pLogHandler) throws UnknownHostException {
        this(null, pDetailsHolder, pRestrictor, pMulticastGroup, pMulticastPort, pLogHandler);
    }

    /**
     * Create the responder which can be started and stopped
     *
     * @param pHostAddress host address from which the binding is performed
     * @param pDetailsHolder holds the details for an agent
     * @param pRestrictor restrictor used for avoiding responding to sites which are not allowed to connect
     * @param pMulticastGroup multicast IPv4 address
     * @param pMulticastPort multicast port
     * @param pLogHandler used for logging and debugging
     */
    public DiscoveryMulticastResponder(InetAddress pHostAddress,
                                       AgentDetailsHolder pDetailsHolder,
                                       Restrictor pRestrictor,
                                       String pMulticastGroup,
                                       int pMulticastPort,
                                       LogHandler pLogHandler) {
        hostAddress = pHostAddress;
        detailsHolder = pDetailsHolder;
        restrictor = pRestrictor;
        multicastGroup = pMulticastGroup;
        multicastPort = pMulticastPort;
        logHandler = pLogHandler;
        listenerThreads = new ArrayList<MulticastSocketListenerThread>();
    }

    /**
     * Start the responder (if not already started)
     */
    public synchronized void start() throws IOException {
        if (listenerThreads.size() == 0) {
            List<InetAddress> addresses = hostAddress == null ? NetworkUtil.getMulticastAddresses() : Arrays.asList(hostAddress);
            if (addresses.size() == 0) {
                logHandler.info("No suitable address found for listening on multicast discovery requests");
                return;
            }
            // We start a thread for every address found
            for (InetAddress addr : addresses) {
                try {
                    MulticastSocketListenerThread thread = new MulticastSocketListenerThread("JolokiaDiscoveryListenerThread-" + addr.getHostAddress(),
                                                                                            addr,
                                                                                             detailsHolder,
                                                                                             restrictor,
                                                                                             multicastGroup,
                                                                                             multicastPort,
                                                                                             logHandler);
                    thread.start();
                    listenerThreads.add(thread);
                } catch (IOException exp) {
                    logHandler.info("Couldn't start discovery thread for " + addr + ": " + exp);
                }
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
