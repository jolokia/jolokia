package org.jolokia.discovery;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import org.jolokia.util.LogHandler;
import org.jolokia.util.NetworkUtil;

/**
 * Utility class for handling multicast stuff
 *
 * @author roland
 * @since 28.01.14
 */
public final class MulticastUtil {

    // IPv4 Address for Jolokia's Multicast group
    public static final String JOLOKIA_MULTICAST_GROUP = "239.192.48.84";

    // Multicast port where to listen for queries
    public static final int JOLOKIA_MULTICAST_PORT = 24884;

    // Utility class
    private MulticastUtil() {
    }

    static MulticastSocket newMulticastSocket(InetAddress pAddress) throws IOException {
        // TODO: IpV6 (not supported yet)
        InetSocketAddress socketAddress =
                new InetSocketAddress(JOLOKIA_MULTICAST_GROUP, JOLOKIA_MULTICAST_PORT);

        MulticastSocket socket = new MulticastSocket(JOLOKIA_MULTICAST_PORT);
        socket.setReuseAddress(true);
        setOutgoingInterfaceForMulticastRequest(pAddress, socket);
        socket.setTimeToLive(255);
        joinMcGroupsOnAllNetworkInterfaces(socket, socketAddress);
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
    public static List<DiscoveryIncomingMessage> sendQueryAndCollectAnswers(DiscoveryOutgoingMessage pOutMsg, int pTimeout) throws IOException {
        return sendQueryAndCollectAnswers(pOutMsg, pTimeout, LogHandler.QUIET);
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
                                                                            LogHandler pLogHandler) throws IOException {
        final List<Future<List<DiscoveryIncomingMessage>>> futures = sendDiscoveryRequests(pOutMsg, pTimeout, pLogHandler);
        return collectIncomingMessages(pTimeout, futures);
    }

    // ==============================================================================================================
    // Send requests in parallel threads, return the futures for getting the result
    private static List<Future<List<DiscoveryIncomingMessage>>> sendDiscoveryRequests(DiscoveryOutgoingMessage pOutMsg,
                                                                                      int pTimeout,
                                                                                      LogHandler pLogHandler) throws SocketException, UnknownHostException {
        // Note for Ipv6 support: If there are two local addresses, one with IpV6 and one with IpV4 then two discovery request
        // should be sent, on each interface respectively. Currently, only IpV4 is supported.
        List<InetAddress> addresses = getMulticastAddresses();
        ExecutorService executor = Executors.newFixedThreadPool(addresses.size());
        final List<Future<List<DiscoveryIncomingMessage>>> futures = new ArrayList<Future<List<DiscoveryIncomingMessage>>>(addresses.size());
        for (InetAddress address : addresses) {
            // Discover UDP packet send to multicast address
            DatagramPacket out = pOutMsg.createDatagramPacket(InetAddress.getByName(JOLOKIA_MULTICAST_GROUP), JOLOKIA_MULTICAST_PORT);
            Callable<List<DiscoveryIncomingMessage>> findAgentsCallable = new FindAgentsCallable(address, out, pTimeout, pLogHandler);
            futures.add(executor.submit(findAgentsCallable));
        }
        return futures;
    }

    // All addresses which can be used for sending multicast addresses
    private static List<InetAddress> getMulticastAddresses() throws SocketException, UnknownHostException {
        List<InetAddress> addresses = NetworkUtil.getMulticastAddresses();
        if (addresses.size() == 0) {
            throw new UnknownHostException("Cannot find address of local host which can be used for sending discover package");
        }
        return addresses;
    }

    // Collect the incoming messages and filter out duplicates
    private static List<DiscoveryIncomingMessage> collectIncomingMessages(int pTimeout, List<Future<List<DiscoveryIncomingMessage>>> pFutures) {
        List<DiscoveryIncomingMessage> ret = new ArrayList<DiscoveryIncomingMessage>();
        Set<String> seen = new HashSet<String>();
        System.out.println("Timeout: " + pTimeout);
        for (Future<List<DiscoveryIncomingMessage>> future : pFutures) {
            try {
                List<DiscoveryIncomingMessage> inMsgs = future.get(pTimeout + 500 /* some additional buffer */, TimeUnit.MILLISECONDS);
                System.out.println(">>>> inMsgs: " + inMsgs + "(" + inMsgs.size() + ")" );
                for (DiscoveryIncomingMessage inMsg : inMsgs) {
                    AgentDetails details = inMsg.getAgentDetails();
                    String id = details.getAgentId();
                    System.out.println(" -- id: " + id + ", seen = " + seen.contains(id));
                    // There can be multiples answers with the same message id
                    if (!seen.contains(id)) {
                        ret.add(inMsg);
                        seen.add(id);
                    }
                }
            } catch (InterruptedException exp) {
                exp.printStackTrace();
                // Try next one ...
            } catch (ExecutionException e) {
                e.printStackTrace();
                // Didn't worked a given address, which can happen e.g. when multicast is not routed or in other cases
                // throw new IOException("Error while performing a discovery call " + e,e);
            } catch (TimeoutException e) {
                // Timeout occurred while waiting for the results. So we go to the next one ...
               e.printStackTrace();
            }
        }
        return ret;
    }

    // We are using all interfaces available and try to join them
    private static void joinMcGroupsOnAllNetworkInterfaces(MulticastSocket pSocket, InetSocketAddress pSocketAddress) throws IOException {
        // V6: ffx8::/16
        Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
        while (nifs.hasMoreElements()) {
            NetworkInterface n = nifs.nextElement();
            if (NetworkUtil.isMulticastSupported(n)) {
                pSocket.joinGroup(pSocketAddress, n);
            }
        }
    }

    private static void setOutgoingInterfaceForMulticastRequest(InetAddress pAddress, MulticastSocket pSocket) throws SocketException, UnknownHostException {
        NetworkInterface nif = NetworkInterface.getByInetAddress(pAddress);
        if (nif != null) {
            pSocket.setNetworkInterface(nif);
        }
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

        public List<DiscoveryIncomingMessage> call() throws SocketException {
            final DatagramSocket socket = new DatagramSocket(0, address);

            List<DiscoveryIncomingMessage> ret = new ArrayList<DiscoveryIncomingMessage>();
            try {
                socket.setSoTimeout(timeout);
                logHandler.debug(address + "--> Sending");
                socket.send(outPacket);

                try {
                    do {
                        byte[] buf = new byte[AbstractDiscoveryMessage.MAX_MSG_SIZE];
                        DatagramPacket in = new DatagramPacket(buf, buf.length);
                        socket.receive(in);
                        logHandler.debug(address + "--> Received answer");
                        DiscoveryIncomingMessage inMsg = new DiscoveryIncomingMessage(in);
                        if (!inMsg.isQuery()) {
                            ret.add(inMsg);
                        }
                    } while (true); // Leave loop with a SocketTimeoutException in receive()
                } catch (SocketTimeoutException exp) {
                    logHandler.debug(address + "--> Timeout");
                    // Expected until no responses are returned anymore
                }
                return ret;
            } catch (IOException exp) {
                logHandler.debug(address + "--> Could not send multicast over : " + exp);
                return ret;
            } finally {
                socket.close();
            }
        }
    }
}
