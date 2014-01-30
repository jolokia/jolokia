// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package org.jolokia.discovery.multicast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;

import org.jolokia.detector.ServerHandle;

/**
 * Listen for multicast packets.
 */
class SocketListener implements Runnable {

    private final MulticastSocket socket;
    private boolean running;
    private ServerHandle handle;

    SocketListener(MulticastSocket pSocket,ServerHandle pHandle) {
        socket = pSocket;
        handle = pHandle;
        running = true;
    }

    public void run() {
        try {
            byte buf[] = new byte[AbstractDiscoveryMessage.MAX_MSG_SIZE];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            while (isRunning()) {
                packet.setLength(buf.length);
                socket.receive(packet);
                try {
                    DiscoveryIncomingMessage msg = new DiscoveryIncomingMessage(packet);
                    if (msg.isQuery()) {
                        handleQuery(msg);
                    }
                } catch (IOException e) {
                    // TODO: Logging if required
                }
            }
        } catch (IOException e) {
            // TODO: Log or handle it here
        }
    }

    private void handleQuery(DiscoveryIncomingMessage pMsg) throws IOException {
        DiscoveryOutgoingMessage answer =
                new DiscoveryOutgoingMessage.Builder(AbstractDiscoveryMessage.MessageType.RESPONSE)
                        .respondTo(pMsg)
                        .handle(handle)
                        .build();
        send(answer);
    }

    private void send(DiscoveryOutgoingMessage pAnswer) throws IOException {
        byte[] message = pAnswer.getData();
        final DatagramPacket packet =
                new DatagramPacket(message, message.length,
                                   pAnswer.getTargetAddress(),pAnswer.getTargetPort());

        if (socket != null && !socket.isClosed()) {
            socket.send(packet);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean pRunning) {
        running = pRunning;
    }

}
