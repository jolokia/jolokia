package org.jolokia.discovery;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;

/**
 * Utility class for handling multicast stuff
 *
 * @author roland
 * @since 28.01.14
 */
public class MulticastUtil {

    // IPv4 Address for Jolokia's Multicast group
    public static final String JOLOKIA_MULTICAST_GROUP_IP4 = "239.192.48.84";

    // IpV6 multicast address (not yet supported)
    public static final String JOLOKIA_MULTICAST_GROUP_IP6 = "FF08::48:84";

    // Multicast port where to listen for queries
    public static final int JOLOKIA_MULTICAST_PORT = 24884;

    // Only available for Java 6
    private static Method isUp;
    private static Method supportsMulticast;

    static {
        // Check for JDK method which are available only for JDK6
        try {
            isUp = NetworkInterface.class.getMethod("isUp", (Class<?>[]) null);
            supportsMulticast = NetworkInterface.class.getMethod("supportsMulticast", (Class<?>[]) null);
        } catch (NoSuchMethodException e) {
            isUp = null;
            supportsMulticast = null;
        }
    }

    static MulticastSocket newMulticastSocket() throws IOException {
        return newMulticastSocket(null);
    }

    static MulticastSocket newMulticastSocket(InetAddress pAddress) throws IOException {

        // TODO: IpV6
        InetAddress address = pAddress != null && pAddress instanceof Inet4Address ? pAddress : getLocalAddress();
        if (address == null) {
            throw new UnknownHostException("Cannot find address of local host which can be used for multicasting");
        }
        MulticastSocket socket = new MulticastSocket(JOLOKIA_MULTICAST_PORT);
        socket.setReuseAddress(true);
        if (address instanceof Inet6Address) {
            throw new IllegalArgumentException("Wrong address " + address + " found");
        }
        System.out.println("Address: " + address);
        System.out.println("NI: " + NetworkInterface.getByInetAddress(address));
        socket.setNetworkInterface(NetworkInterface.getByInetAddress(address));
        socket.setTimeToLive(255);
        // V6: ffx8::/16
        socket.joinGroup(getMulticastGroup(address));
        return socket;
    }

    private static InetAddress getMulticastGroup(InetAddress pAddress) throws UnknownHostException {
        return InetAddress.getByName(pAddress instanceof Inet6Address ? JOLOKIA_MULTICAST_GROUP_IP6 : JOLOKIA_MULTICAST_GROUP_IP4);
    }

    public static List<DiscoveryIncomingMessage> sendQueryAndCollectAnswers(DiscoveryOutgoingMessage pOutMsg) throws IOException {
        // Note for Ipv6 support: If there are two local addresses, one with IpV6 and one with IpV4 then two discovery request
        // should be sent, on each interface respectively. Currently, only IpV4 is supported.
        InetAddress address = getLocalAddress();
        if (address == null) {
            throw new UnknownHostException("Cannot find address of local host which can be used for sending discover package");
        }
        final DatagramSocket socket = new DatagramSocket(0,address);
        try {
            socket.setSoTimeout(1000);

            // Discover UDP packet send to multicast address
            DatagramPacket out = pOutMsg.getDatagramPacket(getMulticastGroup(address),
                                                           JOLOKIA_MULTICAST_PORT);
            socket.send(out);

            List<DiscoveryIncomingMessage> ret = new ArrayList<DiscoveryIncomingMessage>();
            try {
                do {
                    byte[] buf = new byte[AbstractDiscoveryMessage.MAX_MSG_SIZE];
                            DatagramPacket in = new DatagramPacket(buf, buf.length);
                    socket.receive(in);
                    DiscoveryIncomingMessage inMsg = new DiscoveryIncomingMessage(in);
                    if (!inMsg.isQuery()) {
                        ret.add(inMsg);
                    }
                } while (true); // should leave loop by SocketTimeoutException
            } catch (SocketTimeoutException exp) {
                // Expected
            }
            return ret;
        } finally {
            socket.close();
        }
    }

    private static InetAddress getLocalAddress() throws UnknownHostException, SocketException {
        InetAddress addr = InetAddress.getLocalHost();
        if (addr.isLoopbackAddress() || addr instanceof Inet6Address || NetworkInterface.getByInetAddress(addr) == null) {
            // Find local address that isn't a loopback address
            InetAddress lookedUpAddr = findLocalAddress();
            // If a local, multicast enabled address can be found, use it. Otherwise
            // we are using the local address, which might not be what you want
            if (lookedUpAddr != null) {
                addr = lookedUpAddr;
            }
        }
        return addr;
    }

    private static InetAddress findLocalAddress() {
        Enumeration<NetworkInterface> networkInterfaces;
        try {
            networkInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return null;
        }

        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface nif = networkInterfaces.nextElement();
            for (Enumeration<InetAddress> addrEnum = nif.getInetAddresses(); addrEnum.hasMoreElements();) {
                InetAddress interfaceAddress = addrEnum.nextElement();
                if (useInetAddress(nif, interfaceAddress)) {
                    return interfaceAddress;
                }
            }
        }
        return null;
    }

    // Only use the given interface on the given network interface if it is up and supports multicast
    private static boolean useInetAddress(NetworkInterface networkInterface, InetAddress interfaceAddress) {
        return checkMethod(networkInterface, isUp) &&
               checkMethod(networkInterface, supportsMulticast) &&
               // TODO: IpV6 support
               ! (interfaceAddress instanceof Inet6Address) &&
               !interfaceAddress.isLoopbackAddress();
    }

    // Call a method and return the result as boolean. In case of problems, return false.
    private static Boolean checkMethod(NetworkInterface iface, Method toCheck) {
        if (toCheck != null) {
            try {
                return (Boolean) toCheck.invoke(iface, (Object[]) null);
            } catch (IllegalAccessException e) {
                return false;
            } catch (InvocationTargetException e) {
                return false;
            }
        }
        return false;
    }


}
