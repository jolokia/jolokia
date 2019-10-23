// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package org.jolokia.discovery;

import java.io.IOException;
import java.net.*;

import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.LogHandler;
import org.jolokia.util.NetworkUtil;

import static org.jolokia.discovery.AbstractDiscoveryMessage.MessageType.RESPONSE;

/**
 * A listener runnable which should be used in thread and which reads from a multicast socket
 * incoming request to which it responds with this agent details.
 *
 * @since 31.01.2014
 */
class MulticastSocketListenerThread extends Thread {

    // From where to get agent details
    private final AgentDetailsHolder agentDetailsHolder;

    // Restrictor checking for remote address
    private final Restrictor restrictor;

    private final String multicastGroup;
    private final int multicastPort;

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
     * @param name the name of the new thread
     * @param pHostAddress host address for creating a socket to listen to
     * @param pAgentDetailsHolder the holder which has the agent details
     * @param pRestrictor restrictor to check whether an incoming package should be answered which
     *                    is done only when {@link Restrictor#isRemoteAccessAllowed(String...)} returns true for
     *                    the address from which the packet was received.
     * @param pMulticastGroup multicast IPv4 address
     * @param pMulticastPort multicast port
     * @param pLogHandler log handler used for logging
     * @throws IOException if join multicast group fails
     */
    MulticastSocketListenerThread(String name, InetAddress pHostAddress, AgentDetailsHolder pAgentDetailsHolder, Restrictor pRestrictor, String pMulticastGroup, int pMulticastPort, LogHandler pLogHandler) throws IOException {
        super(name);
        address = pHostAddress != null ? pHostAddress : NetworkUtil.getLocalAddressWithMulticast();
        agentDetailsHolder = pAgentDetailsHolder;
        restrictor = pRestrictor;
        multicastGroup = pMulticastGroup;
        multicastPort = pMulticastPort;
        logHandler = pLogHandler;
        // For debugging, uncomment:
        //logHandler = new LogHandler.StdoutLogHandler(true);

        socket = MulticastUtil.newMulticastSocket(address,multicastGroup,multicastPort,logHandler);
        logHandler.debug(address + "<-- Listening for queries");
        setDaemon(true);
    }

    /** {@inheritDoc} */
    public void run() {
        setRunning(true);
        try {
            while (isRunning()) {
                refreshSocket();
                logHandler.debug(address + "<-- Waiting");
                DiscoveryIncomingMessage msg = receiveMessage();
                if (shouldMessageBeProcessed(msg)) {
                    handleQuery(msg);
                }
            }
        }
        catch (IllegalStateException e) {
            logHandler.error(address + "<-- Cannot reopen socket, exiting listener thread: " + e.getCause(),e.getCause());
        } finally {
            if (socket != null) {
                socket.close();
            }
            logHandler.debug(address + "<-- Stop listening");
        }
    }

    private synchronized void setRunning(boolean pRunning) {
        running = pRunning;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void shutdown() {
        setRunning(false);
        interrupt();
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
            logHandler.info(address + "<-- Socket closed, reopening it");
            try {
                socket = MulticastUtil.newMulticastSocket(address, multicastGroup, multicastPort, logHandler);
            } catch (IOException exp) {
                logHandler.error("Cannot reopen socket. Exiting multicast listener thread ...",exp);
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
        logHandler.debug(address + "<-- Discovery request from " + pMsg.getSourceAddress() + ":" + pMsg.getSourcePort());
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
                logHandler.info(address + "<-- Can not send discovery response to " + packet.getAddress());
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
