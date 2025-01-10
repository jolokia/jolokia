package org.jolokia.service.discovery;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.service.api.AgentDetails;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.api.LogHandler;
import org.jolokia.server.core.service.impl.QuietLogHandler;
import org.jolokia.server.core.util.NetworkUtil;

/**
 * Utility class for handling multicast stuff
 *
 * @author roland
 * @since 28.01.14
 */
public final class MulticastUtil {

    // Utility class
    private MulticastUtil() {
    }

    /**
     * Create new {@link MulticastSocket} joining Jolokia Multicast group. If {@code pAddress} is not specified, the
     * socket is bound to <em>any</em> interface (IPv4: 0.0.0.0 or IPv6: * (both 0.0.0.0 and [::])).
     *
     * @param pAddress can be used to select particular address to bind to. If not passed, 0.0.0.0 + [::] is used.
     * @param pContext
     * @return
     * @throws IOException
     */
    static MulticastSocket newMulticastSocket(InetAddress pAddress, JolokiaContext pContext) throws IOException {
        String multicastGroup = pContext.getConfig(ConfigKey.MULTICAST_GROUP, true);
        int multicastPort = Integer.parseInt(pContext.getConfig(ConfigKey.MULTICAST_PORT, true));

        InetSocketAddress mcSocketAddress = new InetSocketAddress(multicastGroup, multicastPort);

        // java.net.InetAddress.anyLocalAddress will be used when specifying port only
        MulticastSocket socket = pAddress == null
            ? new MulticastSocket(multicastPort)
            : new MulticastSocket(new InetSocketAddress(pAddress, multicastPort));
        socket.setTimeToLive(255);

        // java.net.DatagramSocket.setReuseAddress(true) is called automatically for MulticastSocket
        // sun.nio.ch.Net.bind() checks preferIPv6 flag based on IPv6 availability, so even if IPv6 addresses
        // are preferred, the socket may ultimately be bound on [::] address, which can be
        // obtained (after bind()) from sun.nio.ch.DatagramChannelImpl.localAddress

        // We had setOutgoingInterfaceForMulticastRequest(pAddress, socket) call which used
        // java.net.MulticastSocket.setNetworkInterface(), but this is for sending, not receiving.

        if (joinMcGroupsOnAllNetworkInterfaces(socket, mcSocketAddress, pContext) == 0) {
            throw new IOException("Couldn't join multicast group " + mcSocketAddress + " on any network interfaces");
        }
        return socket;
    }

    /**
     * Sent out a message to Jolokia's multicast group over all network interfaces supporting multicast request (and no
     * logging is used)
     *
     * @param pOutMsg  the message to send
     * @param pTimeout timeout used for how long to wait for discovery messages
     * @return list of received answers, never null
     * @throws IOException if something fails during the discovery request
     */
    public static List<DiscoveryIncomingMessage> sendQueryAndCollectAnswers(DiscoveryOutgoingMessage pOutMsg, int pTimeout, String pMulticastGroup, int pMulticastPort) throws IOException {
        return sendQueryAndCollectAnswers(pOutMsg, pTimeout, pMulticastGroup, pMulticastPort, new QuietLogHandler());
    }

    /**
     * Sent out a message to Jolokia's multicast group over all network interfaces supporting multicasts
     *
     * @param pOutMsg     the message to send
     * @param pTimeout    timeout used for how long to wait for discovery messages
     * @param pLogHandler a log handler for printing out logging information
     * @return list of received answers, never null
     * @throws IOException if something fails during the discovery request
     */
    public static List<DiscoveryIncomingMessage> sendQueryAndCollectAnswers(DiscoveryOutgoingMessage pOutMsg,
                                                                            int pTimeout,
                                                                            String pMulticastGroup,
                                                                            int pMulticastPort,
                                                                            LogHandler pLogHandler) throws IOException {
        final List<Future<List<DiscoveryIncomingMessage>>> futures = sendDiscoveryRequests(pOutMsg, pTimeout, pMulticastGroup, pMulticastPort, pLogHandler);
        return collectIncomingMessages(pTimeout, futures, pLogHandler);
    }

    // ==============================================================================================================
    // Send requests in parallel threads, return the futures for getting the result
    private static List<Future<List<DiscoveryIncomingMessage>>> sendDiscoveryRequests(DiscoveryOutgoingMessage pOutMsg,
                                                                                      int pTimeout,
                                                                                      String pMulticastGroup,
                                                                                      int pMulticastPort,
                                                                                      LogHandler pLogHandler) throws UnknownHostException {
        // Note for Ipv6 support: If there are two local addresses, one with IpV6 and one with IpV4 then two discovery request
        // should be sent, on each interface respectively. Currently, only IpV4 is supported.
        List<InetAddress> addresses = getMulticastAddresses();
        ExecutorService executor = Executors.newFixedThreadPool(addresses.size());
        final List<Future<List<DiscoveryIncomingMessage>>> futures = new ArrayList<>(addresses.size());
        for (InetAddress address : addresses) {
            // Discover UDP packet send to multicast address
            DatagramPacket out = pOutMsg.createDatagramPacket(InetAddress.getByName(pMulticastGroup), pMulticastPort);
            Callable<List<DiscoveryIncomingMessage>> findAgentsCallable = new FindAgentsCallable(address, out, pTimeout, pLogHandler);
            futures.add(executor.submit(findAgentsCallable));
        }
        executor.shutdownNow();
        return futures;
    }

    // All addresses which can be used for sending multicast addresses
    private static List<InetAddress> getMulticastAddresses() throws UnknownHostException {
        List<InetAddress> addresses = NetworkUtil.getMulticastAddresses();
        if (addresses.isEmpty()) {
            throw new UnknownHostException("Cannot find address of local host which can be used for sending discovery request");
        }
        return addresses;
    }

    // Collect the incoming messages and filter out duplicates
    private static List<DiscoveryIncomingMessage> collectIncomingMessages(int pTimeout, List<Future<List<DiscoveryIncomingMessage>>> pFutures, LogHandler pLogHandler) throws UnknownHostException {
        List<DiscoveryIncomingMessage> ret = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int nrCouldntSend = 0;
        for (Future<List<DiscoveryIncomingMessage>> future : pFutures) {
            try {
                List<DiscoveryIncomingMessage> inMsgs = future.get(pTimeout + 500 /* some additional buffer */, TimeUnit.MILLISECONDS);
                for (DiscoveryIncomingMessage inMsg : inMsgs) {
                    AgentDetails details = inMsg.getAgentDetails();
                    String id = details.getAgentId();
                    // There can be multiples answers with the same message id
                    if (!seen.contains(id)) {
                        ret.add(inMsg);
                        seen.add(id);
                    }
                }
            } catch (InterruptedException exp) {
                // Try next one ...
            } catch (ExecutionException e) {
                Throwable exp = e.getCause();
                if (exp instanceof CouldntSendDiscoveryPacketException) {
                    nrCouldntSend++;
                    pLogHandler.debug("--> Couldnt send discovery message from " +
                                      ((CouldntSendDiscoveryPacketException) exp).getAddress() + ": " + exp.getCause());
                }
                // Didn't worked a given address, which can happen e.g. when multicast is not routed or in other cases
                // throw new IOException("Error while performing a discovery call " + e,e);
                pLogHandler.debug("--> Exception during lookup: " + e);
            } catch (TimeoutException e) {
                // Timeout occurred while waiting for the results. So we go to the next one ...
            }
        }
        if (nrCouldntSend == pFutures.size()) {
            // No a single discovery message could be send out
            throw new UnknownHostException("Cannot send a single multicast recovery request on any multicast enabled interface");
        }
        return ret;
    }

    // We are using all interfaces available and try to join them
    private static int joinMcGroupsOnAllNetworkInterfaces(MulticastSocket pSocket, InetSocketAddress pMCAddress, LogHandler pLogHandler) throws IOException {
        Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
        int interfacesJoined = 0;
        while (nifs.hasMoreElements()) {
            NetworkInterface n = nifs.nextElement();
            if (NetworkUtil.isMulticastSupported(n)) {
                try {
                    pLogHandler.debug(pSocket.getLocalSocketAddress() + " <-- Joining MC group " + pMCAddress + " on interface " + n);
                    pSocket.joinGroup(pMCAddress, n);
                    interfacesJoined++;
                } catch (IOException exp) {
                    pLogHandler.info("Cannot join multicast group on NIF " + n.getDisplayName() + ": " + exp.getMessage());
                }
            }
        }
        return interfacesJoined;
    }

    private static final class FindAgentsCallable implements Callable<List<DiscoveryIncomingMessage>> {
        private final InetAddress address;
        private final DatagramPacket outPacket;
        private final int timeout;
        private final LogHandler logHandler;

        private FindAgentsCallable(InetAddress pAddress, DatagramPacket pOutPacket, int pTimeout, LogHandler pLogHandler) {
            address = pAddress;
            outPacket = pOutPacket;
            timeout = pTimeout;
            logHandler = pLogHandler;
        }

        public List<DiscoveryIncomingMessage> call() throws IOException {
            final DatagramSocket socket = new DatagramSocket(0, address);

            try (socket) {
                List<DiscoveryIncomingMessage> ret = new ArrayList<>();
                try {
                    socket.setSoTimeout(timeout);
                    logHandler.debug(address + "--> Sending");
                    socket.send(outPacket);
                } catch (IOException exp) {
                    throw new CouldntSendDiscoveryPacketException(
                            address,
                            "Can't send discovery UDP packet from " + address + ": " + exp.getMessage(),
                            exp);
                }

                try {
                    do {
                        byte[] buf = new byte[AbstractDiscoveryMessage.MAX_MSG_SIZE];
                        DatagramPacket in = new DatagramPacket(buf, buf.length);
                        socket.receive(in);
                        logHandler.debug(address + "--> Received answer from " + in.getAddress());
                        addIncomingMessage(ret, in);
                    } while (true); // Leave loop with a SocketTimeoutException in receive()
                } catch (SocketTimeoutException exp) {
                    logHandler.debug(address + "--> Timeout");
                    // Expected until no responses are returned anymore
                } catch (IOException exp) {
                    throw new IOException("Cannot receive broadcast answer on " + address + ": " + exp.getMessage(), exp);
                }
                return ret;
            }
        }

        private void addIncomingMessage(List<DiscoveryIncomingMessage> ret, DatagramPacket in) {
            try {
                DiscoveryIncomingMessage inMsg = new DiscoveryIncomingMessage(in);
                if (!inMsg.isQuery()) {
                    ret.add(inMsg);
                }
            } catch (Exception exp) {
                logHandler.debug("Invalid incoming package from " + in.getAddress() + "  --> " + exp + ". Ignoring");
            }
        }

    }

    private static class CouldntSendDiscoveryPacketException extends IOException {
        private final InetAddress address;

        public CouldntSendDiscoveryPacketException(InetAddress pAddress, String pMessage, IOException pNested) {
            super(pMessage,pNested);
            this.address = pAddress;
        }

        public InetAddress getAddress() {
            return address;
        }
    }
}
