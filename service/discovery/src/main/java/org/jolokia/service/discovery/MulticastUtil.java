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
import org.jolokia.server.core.util.NetworkInterfaceAndAddress;
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

    public static String getReadableSocketName(MulticastSocket socket) {
        if (socket == null || socket.isClosed()) {
            return "???:-1";
        }

        InetAddress localAddress = socket.getLocalAddress();
        int localPort = socket.getLocalPort();
        return getReadableSocketName(localAddress, localPort);
    }

    public static String getReadableSocketName(InetAddress address, int port) {
        if (address instanceof Inet6Address) {
            return String.format("[%s]:%d", address.getHostAddress(), port);
        } else {
            return address.getHostAddress() + ":" + port;
        }
    }

    // ==============================================================================================================
    // Send requests in parallel threads, return the futures for getting the result
    private static List<Future<List<DiscoveryIncomingMessage>>> sendDiscoveryRequests(DiscoveryOutgoingMessage pOutMsg,
                                                                                      int pTimeout,
                                                                                      String pMulticastGroup,
                                                                                      int pMulticastPort,
                                                                                      LogHandler pLogHandler) throws UnknownHostException {
        List<NetworkInterfaceAndAddress> addresses = getMulticastAddresses();
        ExecutorService executor = Executors.newFixedThreadPool(addresses.size());
        final List<Future<List<DiscoveryIncomingMessage>>> futures = new ArrayList<>(addresses.size());
        // Discover UDP packet send to multicast address - packet contains the target socket address and may
        // be created once for each source socket/address
        DatagramPacket out = pOutMsg.createDatagramPacket(InetAddress.getByName(pMulticastGroup), pMulticastPort);
        boolean targetIsIPv4 = out.getAddress() instanceof Inet4Address;
        for (NetworkInterfaceAndAddress pair : addresses) {
            // we know that NetworkInterface is up and supports multicast, but let's skip some address scopes/classes
            InetAddress address = pair.address;
            if (address.isLinkLocalAddress()) {
                // 169.254.0.0/16 or [fe80::]/64 kind of address
                pLogHandler.debug(getReadableSocketName(address, 0)
                    + " --> " + getReadableSocketName(out.getAddress(), out.getPort()) + " - Skipping link local address");
                continue;
            }
            if (address instanceof Inet6Address && targetIsIPv4
                    || address instanceof Inet4Address && !targetIsIPv4) {
                // skip silently, as we don't want to mix protocols
                continue;
            }
            Callable<List<DiscoveryIncomingMessage>> findAgentsCallable = new FindAgentsCallable(pair, out, pTimeout, pLogHandler);
            futures.add(executor.submit(findAgentsCallable));
        }
        executor.shutdownNow();
        return futures;
    }

    // All addresses which can be used for sending multicast addresses
    private static List<NetworkInterfaceAndAddress> getMulticastAddresses() throws UnknownHostException {
        List<NetworkInterfaceAndAddress> addresses = NetworkUtil.getMulticastAddresses();
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
                    String source = getReadableSocketName(((CouldntSendDiscoveryPacketException) exp).getAddress(), ((CouldntSendDiscoveryPacketException) exp).getPort());
                    String target = ((CouldntSendDiscoveryPacketException) exp).getTarget();
                    pLogHandler.debug(source + " --> " + target + " - Couldn't send discovery request: " + exp.getMessage());
                } else {
                    // Didn't worked a given address, which can happen e.g. when multicast is not routed or in other cases
                    // throw new IOException("Error while performing a discovery call " + e,e);
                    pLogHandler.debug("Exception during lookup: " + e);
                }
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
                    pLogHandler.debug(getReadableSocketName(pSocket) + " +-- Joining MC group "
                        + getReadableSocketName(pMCAddress.getAddress(), pMCAddress.getPort()) + " on interface " + n.getName());
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
        private final NetworkInterfaceAndAddress pair;
        private final DatagramPacket outPacket;
        private final int timeout;
        private final LogHandler logHandler;

        private FindAgentsCallable(NetworkInterfaceAndAddress pAddress, DatagramPacket pOutPacket, int pTimeout, LogHandler pLogHandler) {
            pair = pAddress;
            outPacket = pOutPacket;
            timeout = pTimeout;
            logHandler = pLogHandler;
        }

        public List<DiscoveryIncomingMessage> call() throws IOException {
            final DatagramSocket socket = new DatagramSocket(0, pair.address);

            String source = getReadableSocketName(socket.getLocalAddress(), socket.getLocalPort());
            String target = getReadableSocketName(outPacket.getAddress(), outPacket.getPort());
            try (socket) {
                List<DiscoveryIncomingMessage> ret = new ArrayList<>();
                try {
                    socket.setSoTimeout(timeout);
                    logHandler.debug(source + " --> " + target + " - Sending discovery request via "
                        + pair.networkInterface.getName());
                    socket.send(outPacket);
                } catch (IOException exp) {
                    throw new CouldntSendDiscoveryPacketException(
                        pair, socket.getLocalPort(), target,
                        exp.getMessage(), exp);
                }

                try {
                    do {
                        byte[] buf = new byte[AbstractDiscoveryMessage.MAX_MSG_SIZE];
                        DatagramPacket in = new DatagramPacket(buf, buf.length);
                        socket.receive(in);
                        logHandler.debug(source + " <-- " + getReadableSocketName(in.getAddress(), in.getPort())
                            + " - Received discovery response");
                        addIncomingMessage(ret, in);
                    } while (true); // Leave loop with a SocketTimeoutException in receive()
                } catch (SocketTimeoutException exp) {
                    // Expected when no responses are returned anymore
                    logHandler.debug(source + " <-- " + target + " - Timeout (no more messages)");
                } catch (IOException exp) {
                    throw new IOException("Cannot receive broadcast answer on " + pair + ": " + exp.getMessage(), exp);
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
        private final NetworkInterfaceAndAddress pair;
        private final int localPort;
        private final String target;

        public CouldntSendDiscoveryPacketException(NetworkInterfaceAndAddress pair, int localPort,
                                                   String target, String pMessage, IOException pNested) {
            super(pMessage,pNested);
            this.pair = pair;
            this.localPort = localPort;
            this.target = target;
        }

        public InetAddress getAddress() {
            return pair.address;
        }

        public int getPort() {
            return localPort;
        }

        public NetworkInterface getNetworkInterface() {
            return pair.networkInterface;
        }

        public String getTarget() {
            return target;
        }
    }
}
