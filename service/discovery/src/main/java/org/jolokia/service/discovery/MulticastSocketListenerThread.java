// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package org.jolokia.service.discovery;

import java.io.IOException;
import java.net.*;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.NetworkUtil;

import static org.jolokia.service.discovery.AbstractDiscoveryMessage.MessageType.RESPONSE;

/**
 * A listener runnable which should be used in thread and which reads from a multicast socket
 * incoming request to which it responds with this agent details.
 *
 * @since 31.01.2014
 */
class MulticastSocketListenerThread extends Thread {

    // Jolokia services
    private final JolokiaContext context;

    // Address to bind the MulticastSocket to. Defaults to _any_ address (0.0.0.0 or [::])
    private final InetAddress bindAddress;

    // Lifecycle flag
    private boolean running;

    // Socket used for listening
    private MulticastSocket socket;
    private String socketName;

    /**
     * Constructor, used internally.
     *
     * @param pHostAddress host address for creating a socket to listen to
     * @param pContext context for accessing Jolokia Services
     */
    MulticastSocketListenerThread(String pHostAddress, JolokiaContext pContext) throws IOException {
        super();
        bindAddress = pHostAddress != null ? InetAddress.getByName(pHostAddress) : NetworkUtil.getAnyAddress();
        context = pContext;

        if (!bindAddress.isAnyLocalAddress()) {
            throw new IllegalArgumentException("MulticastSocketListenerThread can't bind UDP socket to " + pHostAddress + ". 0.0.0.0 or [::] has to be used.");
        }

        socket = MulticastUtil.newMulticastSocket(bindAddress, pContext);
        socketName = MulticastUtil.getReadableSocketName(socket);

        pContext.debug(socketName + " |-- Listening for queries");
        setName("JolokiaDiscoveryListenerThread-" + socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort());
        setDaemon(true);
    }

    /** {@inheritDoc} */
    public void run() {
        setRunning(true);
        try {
            while (isRunning()) {
                refreshSocket();
                DiscoveryIncomingMessage msg = receiveMessage();
                if (shouldMessageBeProcessed(msg)) {
                    handleQuery(msg);
                }
            }
        }
        catch (IllegalStateException e) {
            context.error(socketName + " --- Can not reopen socket, exiting listener thread: " + e.getMessage(), e.getCause());
        } finally {
            if (socket != null) {
                socket.close();
            }
            context.debug(socketName + " --- Stop listening");
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
              context.isRemoteAccessAllowed(pMsg.getSourceAddress().getHostAddress())
              && pMsg.isQuery();
    }

    private DiscoveryIncomingMessage receiveMessage() {
        byte[] buf = new byte[AbstractDiscoveryMessage.MAX_MSG_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        try {
            packet.setLength(buf.length);
            socket.receive(packet);
            return new DiscoveryIncomingMessage(packet);
        }  catch (IOException e) {
            if (!socket.isClosed()) {
                context.info(socketName + " <-- " + MulticastUtil.getReadableSocketName(packet.getAddress(), packet.getPort())
                    + " - Error while handling discovery request, ignoring: " + e.getMessage());
            }
            return null;
        }
    }

    private void refreshSocket() {
        if (socket.isClosed()) {
            context.info(socketName + " --- Socket closed, reopening it, listening for queries");
            try {
                socket = MulticastUtil.newMulticastSocket(bindAddress, context);
                socketName = MulticastUtil.getReadableSocketName(socket);
            } catch (IOException exp) {
                int multicastPort = Integer.parseInt(context.getConfig(ConfigKey.MULTICAST_PORT, true));
                String name = MulticastUtil.getReadableSocketName(bindAddress, multicastPort);
                context.error(name + " --- Can not reopen socket. Exiting multicast listener thread...", exp);
                throw new SocketVerificationFailedException(exp);
            }
        }
    }

    private void handleQuery(DiscoveryIncomingMessage pMsg) {
        DiscoveryOutgoingMessage answer =
                new DiscoveryOutgoingMessage.Builder(RESPONSE)
                        .respondTo(pMsg)
                        .agentDetails(context.getAgentDetails())
                        .build();
        context.debug(socketName + " <-- " + MulticastUtil.getReadableSocketName(pMsg.getSourceAddress(), pMsg.getSourcePort()) + " - Received discovery request from external client");
        send(answer);
    }

    private void send(DiscoveryOutgoingMessage pAnswer) {
        byte[] message = pAnswer.getData();
        final DatagramPacket packet =
                new DatagramPacket(message, message.length, pAnswer.getTargetAddress(), pAnswer.getTargetPort());
        if (!socket.isClosed()) {
            try {
                socket.send(packet);
            } catch (IOException exp) {
                context.info(socketName + " --> "
                    + MulticastUtil.getReadableSocketName(packet.getAddress(), packet.getPort())
                    + " - Can not send discovery response: " + exp.getMessage());
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
