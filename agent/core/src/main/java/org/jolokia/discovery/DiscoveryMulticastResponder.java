package org.jolokia.discovery;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.LogHandler;

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
    private final LogHandler logHandler;

    // Listener responsible for creating the response as soon as a discovery request
    // arrives.
    private MulticastSocketListener listener;
    private Thread runner;
    private InetAddress hostAddress;

    /**
     * Create the responder which can be started and stopped and which detects the address to listen on on its own.
     *
     * @param pDetailsHolder holds the details for an agent
     * @param pRestrictor restrictor used for avoiding responding to sites which are not allowed to connect
     * @param pLogHandler used for logging and debugging
     * @throws IOException when the host is not known.
     */
    public DiscoveryMulticastResponder(AgentDetailsHolder pDetailsHolder,
                                       Restrictor pRestrictor,
                                       LogHandler pLogHandler) throws UnknownHostException {
        this(null, pDetailsHolder, pRestrictor, pLogHandler);
    }

    /**
     * Create the responder which can be started and stopped
     *
     * @param pHostAddress host address from which the binding is performed
     * @param pDetailsHolder holds the details for an agent
     * @param pRestrictor restrictor used for avoiding responding to sites which are not allowed to connect
     * @param pLogHandler used for logging and debugging
     */
    public DiscoveryMulticastResponder(InetAddress pHostAddress,
                                       AgentDetailsHolder pDetailsHolder,
                                       Restrictor pRestrictor,
                                       LogHandler pLogHandler) {
        hostAddress = pHostAddress;
        detailsHolder = pDetailsHolder;
        restrictor = pRestrictor;
        logHandler = pLogHandler;
    }

    /**
     * Start the responder (if not already started)
     */
    public void start() throws IOException {
        if (listener == null) {
            listener = new MulticastSocketListener(MulticastUtil.newMulticastSocket(hostAddress),
                                                   detailsHolder,
                                                   restrictor,
                                                   logHandler);
            runner = new Thread(listener);
            runner.start();
        }
    }

    /**
     * Stop the responder (if not already stopped). Can be restarted aftewards.
     */
    public void stop() {
        if (listener != null && listener.isRunning()) {
            listener.stop();
            runner.interrupt();
            runner = null;
            listener = null;
        }
    }
}
