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
    private MulticastUtil() {}

    static MulticastSocket newMulticastSocket(InetAddress pAddress) throws IOException {
        // TODO: IpV6 (not supported yet)
        InetSocketAddress socketAddress =
                new InetSocketAddress(JOLOKIA_MULTICAST_GROUP, JOLOKIA_MULTICAST_PORT);

        MulticastSocket socket = new MulticastSocket(JOLOKIA_MULTICAST_PORT);
        socket.setReuseAddress(true);

        setOutgoingInterfaceForMulticastRequest(pAddress, socket);
        socket.setTimeToLive(255);
        joinMcGroupsOnAllNetworkIntefaces(socket, socketAddress);
        return socket;
    }

    /**
     * Sent out a message to Jolokia's multicast group over all network interfaces supporting multicast request (and no
     * logging is used)
     *
     * @param pOutMsg the message to send
     * @param pTimeout timeout used for how long to wait for discovery messages
     * @return list of received answers, never null
     *
     * @throws IOException if something fails during the discovery request
     */
    public static List<DiscoveryIncomingMessage> sendQueryAndCollectAnswers(DiscoveryOutgoingMessage pOutMsg, int pTimeout) throws IOException {
        return sendQueryAndCollectAnswers(pOutMsg, pTimeout, LogHandler.QUIET);
    }

    /**
     * Sent out a message to Jolokia's multicast group over all network interfaces supporting multicasts
     *
     *
     * @param pOutMsg the message to send
     * @param pTimeout timeout used for how long to wait for discovery messages
     * @param pLogHandler a log handler for printing out logging information
     * @return list of received answers, never null
     *
     * @throws IOException if something fails during the discovery request
     */
    public static List<DiscoveryIncomingMessage> sendQueryAndCollectAnswers(DiscoveryOutgoingMessage pOutMsg,
                                                                            int pTimeout,
                                                                            LogHandler pLogHandler) throws IOException {
        // Note for Ipv6 support: If there are two local addresses, one with IpV6 and one with IpV4 then two discovery request
        // should be sent, on each interface respectively. Currently, only IpV4 is supported.
        List<InetAddress> addresses = NetworkUtil.getMulticastAddresses();
        if (addresses.size() == 0) {
            throw new UnknownHostException("Cannot find address of local host which can be used for sending discover package");
        }
        ExecutorService executor = Executors.newFixedThreadPool(addresses.size());
        //ExecutorService executor = Executors.newSingleThreadExecutor();
        final List<Future<List<DiscoveryIncomingMessage>>> futures = new ArrayList<Future<List<DiscoveryIncomingMessage>>>(addresses.size());
        for (InetAddress address : addresses) {
            // Discover UDP packet send to multicast address
            DatagramPacket out = pOutMsg.createDatagramPacket(InetAddress.getByName(JOLOKIA_MULTICAST_GROUP), JOLOKIA_MULTICAST_PORT);
            Callable<List<DiscoveryIncomingMessage>> findAgentsCallable = new FindAgentsCallable(address,out, pTimeout, pLogHandler);
            futures.add(executor.submit(findAgentsCallable));
        }
        List<DiscoveryIncomingMessage> ret = new ArrayList<DiscoveryIncomingMessage>();
        for (Future<List<DiscoveryIncomingMessage>> future : futures) {
            try {
                // Has been cancelled if overal
                List<DiscoveryIncomingMessage> inMsgs = future.get(pTimeout + 500 /* some additional buffer */, TimeUnit.MILLISECONDS);
                ret.addAll(inMsgs);
            } catch (InterruptedException exp) {
                // Try next one ...
            } catch (ExecutionException e) {
                // Didnt worked a given address, which can happen e.g. when multicast is not routed or in other cases
                // throw new IOException("Error while performing a discovery call " + e,e);
            } catch (TimeoutException e) {
                // Timeout occured while waiting for the results. So we go to the next one ...
            }
        }
        return ret;
    }

    // ==============================================================================================================

    private static InetAddress sanitizeLocalAddress(InetAddress pAddress) throws UnknownHostException, SocketException {
        InetAddress address = pAddress != null && pAddress instanceof Inet4Address ? pAddress : NetworkUtil.getLocalAddress();
        if (address == null) {
            throw new UnknownHostException("Cannot find address of local host which can be used for multicasting");
        }
        return address;
    }

    // We are using all interfaces available and try to join them
    private static void joinMcGroupsOnAllNetworkIntefaces(MulticastSocket pSocket, InetSocketAddress pSocketAddress) throws IOException {
        // V6: ffx8::/16
        Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
        while (nifs.hasMoreElements()) {
            NetworkInterface n = nifs.nextElement();
            pSocket.joinGroup(pSocketAddress, n);
        }
    }

    private static void setOutgoingInterfaceForMulticastRequest(InetAddress pAddress, MulticastSocket pSocket) throws SocketException, UnknownHostException {
        InetAddress address = sanitizeLocalAddress(pAddress);
        NetworkInterface nif = NetworkInterface.getByInetAddress(address);
        if (nif != null) {
            pSocket.setNetworkInterface(nif);
        }
    }


    private final static class FindAgentsCallable implements Callable<List<DiscoveryIncomingMessage>> {
        final private InetAddress address;
        final private DatagramPacket outPacket;
        final private int timeout;
        final private LogHandler logHandler;

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
                    logHandler.debug("Sending from " + address);
                    socket.send(outPacket);

                    try {
                        do {
                            byte[] buf = new byte[AbstractDiscoveryMessage.MAX_MSG_SIZE];
                            DatagramPacket in = new DatagramPacket(buf, buf.length);
                            logHandler.debug(System.currentTimeMillis() + "Receiving answer for " + address);
                            socket.receive(in);
                            logHandler.debug(System.currentTimeMillis() + "Received answer for " + address);
                            DiscoveryIncomingMessage inMsg = new DiscoveryIncomingMessage(in);
                            if (!inMsg.isQuery()) {
                                ret.add(inMsg);
                            }
                        } while (true); // Leave loop with a SocketTimeoutException in receive()
                    } catch (SocketTimeoutException exp) {
                        logHandler.debug("Timeout (from " + address + ")");
                        // Expected until no responses are returned anymore
                    }
                    return ret;
                } catch (IOException exp) {
                    //System.out.println("Exception (" + pAddress + ") : " + exp);
                    //exp.printStackTrace();
                    return ret;
                } finally {
                    socket.close();
                }
            }
    }
}
