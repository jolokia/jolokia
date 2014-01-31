// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package org.jolokia.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.nio.channels.ClosedByInterruptException;

import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.LogHandler;

/**
 * A listener runnable which should be used in thread and which reads from a multicast socket
 * incoming request to which it responds with this agent details.
 *
 * @since 31.01.2014
 */
class MulticastSocketListener implements Runnable {

    // Socket to read from
    private final MulticastSocket socket;

    // From where to get agent details
    private final AgentDetailsHolder agentDetailsHolder;

    // Restrictor checking for remote address
    private final Restrictor restrictor;

    private final LogHandler logHandler;

    // Lifecycle flag
    private boolean running;

    /**
     * Constructor, used internally.
     *
     * @param pSocket socket to get data from
     * @param pAgentDetailsHolder the holder which has the agent details
     * @param pRestrictor restrictor to check whether an incoming package should be answered which
     *                    is done only when {@link Restrictor#isRemoteAccessAllowed(String...)} returns true for
     *                    the address from which the packet was received.
     * @param pLogHandler log handler used for logging
     */
    MulticastSocketListener(MulticastSocket pSocket, AgentDetailsHolder pAgentDetailsHolder, Restrictor pRestrictor, LogHandler pLogHandler) {
        socket = pSocket;
        agentDetailsHolder = pAgentDetailsHolder;
        restrictor = pRestrictor;
        logHandler = pLogHandler;
        running = true;
    }

    /** {@inheritDoc} */
    public void run() {
        byte buf[] = new byte[AbstractDiscoveryMessage.MAX_MSG_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        while (isRunning()) {
            try {
                packet.setLength(buf.length);
                socket.receive(packet);
                DiscoveryIncomingMessage msg = new DiscoveryIncomingMessage(packet);
                if (restrictor.isRemoteAccessAllowed(msg.getSourceAddress().getHostAddress())
                    && msg.isQuery()) {
                    handleQuery(msg);
                }
            } catch (ClosedByInterruptException e) {
                // Ok, lets reevaluate the loop
            } catch (IOException e) {
                logHandler.info("Error while handling discovery request from " + packet.getAddress() + ". Ignoring the request. " + e);
            }
        }
    }

    private void handleQuery(DiscoveryIncomingMessage pMsg) throws IOException {
        DiscoveryOutgoingMessage answer =
                new DiscoveryOutgoingMessage.Builder(AbstractDiscoveryMessage.MessageType.RESPONSE)
                        .respondTo(pMsg)
                        .agentDetails(agentDetailsHolder.getAgentDetails())
                        .build();
        logHandler.debug("Discovery request from " + pMsg.getSourceAddress() + ":" + pMsg.getSourcePort());
        send(answer);
    }

    private void send(DiscoveryOutgoingMessage pAnswer) throws IOException {
        byte[] message = pAnswer.getData();
        final DatagramPacket packet =
                new DatagramPacket(message, message.length,
                                   pAnswer.getTargetAddress(),pAnswer.getTargetPort());

        logHandler.debug(new String(message));
        if (socket != null && !socket.isClosed()) {
            socket.send(packet);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        running = false;
    }

    public void start() {
        running = true;
    }
}
