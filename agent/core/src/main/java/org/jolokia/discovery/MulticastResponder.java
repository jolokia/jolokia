package org.jolokia.discovery;

import java.io.IOException;
import java.net.InetAddress;

import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.LogHandler;

/**
 * A receiver which binds to a multicast sockets and responds to multicast requests.
 * It has lifecycle method for starting and stopping the discovery mechanism.
 *
 * @author roland
 * @since 24.01.14
 */
public class MulticastResponder {

    // Listener responsible for creating the response as soon as a discovery request
    // arrives.
    private MulticastSocketListener listener;
    private Thread runner;

    public MulticastResponder(String pHost, AgentDetailsHolder pDetailsHolder, Restrictor pRestrictor, LogHandler pLogHandler) throws IOException {
        InetAddress address = pHost != null ? InetAddress.getByName(pHost) : null;
        listener = new MulticastSocketListener(MulticastUtil.newMulticastSocket(address),
                                               pDetailsHolder,
                                               pRestrictor,
                                               pLogHandler);
    }

    public void start() {
        if (!listener.isRunning()) {
            listener.start();
            runner = new Thread(listener);
            runner.start();
        }
    }

    public void stop() {
        if (listener.isRunning()) {
            listener.stop();
            runner.interrupt();
            runner = null;
        }
    }
}
