// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package org.jolokia.discovery;

import java.io.IOException;
import java.net.*;

import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.LogHandler;

import static org.jolokia.discovery.AbstractDiscoveryMessage.MessageType.RESPONSE;

/**
 * A listener runnable which should be used in thread and which reads from a multicast socket
 * incoming request to which it responds with this agent details.
 *
 * @since 31.01.2014
 */
class MulticastSocketListener implements Runnable {

    // From where to get agent details
    private final AgentDetailsHolder agentDetailsHolder;

    // Restrictor checking for remote address
    private final Restrictor restrictor;

    private final LogHandler logHandler;

    // Address to listen to
    private final InetAddress address;

    // Lifecycle flag
    private boolean running;

    // Socket used for listening
    private MulticastSocket socket;

    /**
     * Constructor, used internally.
     *
     * @param pHostAddress host address for creating a socket to listen to
     * @param pAgentDetailsHolder the holder which has the agent details
     * @param pRestrictor restrictor to check whether an incoming package should be answered which
     *                    is done only when {@link Restrictor#isRemoteAccessAllowed(String...)} returns true for
     *                    the address from which the packet was received.
     * @param pLogHandler log handler used for logging
     */
    MulticastSocketListener(InetAddress pHostAddress, AgentDetailsHolder pAgentDetailsHolder, Restrictor pRestrictor, LogHandler pLogHandler) throws IOException {
        address = pHostAddress;
        agentDetailsHolder = pAgentDetailsHolder;
        restrictor = pRestrictor;
        logHandler = pLogHandler;
        //logHandler = new StdoutLogHandler();

        logHandler.debug("Listening on " + address);
        socket = MulticastUtil.newMulticastSocket(address);
    }



    /** {@inheritDoc} */
    public void run() {
        setRunning(true);

        try {
            while (isRunning()) {
                refreshSocket();
                logHandler.debug("Start receiving");
                DiscoveryIncomingMessage msg = receiveMessage();
                if (shouldMessageBeProcessed(msg)) {
                    handleQuery(msg);
                }
            }
        }
        catch (IllegalStateException e) {
            logHandler.error("Cannot reopen socket, exiting listener thread: " + e.getCause(),e.getCause());
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private synchronized void setRunning(boolean pRunning) {
        running = pRunning;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void stop() {
        setRunning(false);
        socket.close();
    }

    // ====================================================================================

    private boolean shouldMessageBeProcessed(DiscoveryIncomingMessage pMsg) {
        return pMsg != null &&
               restrictor.isRemoteAccessAllowed(pMsg.getSourceAddress().getHostAddress())
               && pMsg.isQuery();
    }

    private DiscoveryIncomingMessage receiveMessage() {
        byte buf[] = new byte[AbstractDiscoveryMessage.MAX_MSG_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        try {
            packet.setLength(buf.length);
            socket.receive(packet);
            return new DiscoveryIncomingMessage(packet);
        }  catch (IOException e) {
            if (!socket.isClosed()) {
                logHandler.info("Error while handling discovery request" + (packet.getAddress() != null ? " from " + packet.getAddress() : "") +
                                ". Ignoring this request. --> " + e);
            }
            return null;
        }
    }

    private void refreshSocket() {
        if (socket.isClosed()) {
            logHandler.info("Socket closed, reopening it ...");
            try {
                socket = MulticastUtil.newMulticastSocket(address);
            } catch (IOException exp) {
                throw new SocketVerificationFailedException(exp);
            }
        }
    }

    private void handleQuery(DiscoveryIncomingMessage pMsg) {
        DiscoveryOutgoingMessage answer =
                new DiscoveryOutgoingMessage.Builder(RESPONSE)
                        .respondTo(pMsg)
                        .agentDetails(agentDetailsHolder.getAgentDetails())
                        .build();
        logHandler.debug("Discovery request from " + pMsg.getSourceAddress() + ":" + pMsg.getSourcePort());
        send(answer);
    }

    private void send(DiscoveryOutgoingMessage pAnswer) {
        byte[] message = pAnswer.getData();
        final DatagramPacket packet =
                new DatagramPacket(message, message.length,
                                   pAnswer.getTargetAddress(),pAnswer.getTargetPort());
        if (!socket.isClosed()) {
            try {
                socket.send(packet);
            } catch (IOException exp) {
                logHandler.info("Can not send discovery response to " + packet.getAddress());
            }
        }
    }


    // Exception thrown when verification fails
    private static class SocketVerificationFailedException extends RuntimeException {
        public SocketVerificationFailedException(IOException e) {
            super(e);
        }
    }

}
