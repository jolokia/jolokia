package org.jolokia.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;

/**
 * @author roland
 * @since 05.02.14
 */
public class NetworkUtil {

    // Only available for Java 6
    private static Method isUp;
    private static Method supportsMulticast;

    static {
        // Check for JDK metho  d which are available only for JDK6
        try {
            isUp = NetworkInterface.class.getMethod("isUp", (Class<?>[]) null);
            supportsMulticast = NetworkInterface.class.getMethod("supportsMulticast", (Class<?>[]) null);
        } catch (NoSuchMethodException e) {
            isUp = null;
            supportsMulticast = null;
        }
    }

    // Utility class
    private NetworkUtil() {}

    // Debug info
    public static void main(String[] args) throws UnknownHostException, SocketException {
        System.out.println(dumpLocalNetworkInfo()); // NOSONAR
    }

    public static InetAddress getLocalAddress() throws UnknownHostException, SocketException {
        InetAddress addr = InetAddress.getLocalHost();
        if (addr.isLoopbackAddress() || addr instanceof Inet6Address || NetworkInterface.getByInetAddress(addr) == null) {
            // Find local address that isn't a loopback address
            InetAddress lookedUpAddr = findLocalAddressViaNetworkInterface();
            // If a local, multicast enabled address can be found, use it. Otherwise
            // we are using the local address, which might not be what you want
            addr = lookedUpAddr != null ? lookedUpAddr : InetAddress.getByName("127.0.0.1");
        }
        return addr;
    }

    // returns null if none has been found
    public static InetAddress findLocalAddressViaNetworkInterface() {
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

    /**
     * Check, whether multicast is supported at all
     *
     * @return true if at least one network interface supports multicast
     */
    public static boolean isMulticastSupported() throws SocketException {
        return getMulticastAddresses().size() != 0;
    }

    /**
     * Get all local addresses on which a multicast can be send
     * @return list of all multi cast capable addresses
     */
    public static List<InetAddress> getMulticastAddresses() throws SocketException {
        Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
        List<InetAddress> ret = new ArrayList<InetAddress>();
        while (nifs.hasMoreElements()) {
            NetworkInterface nif = nifs.nextElement();
            if (checkMethod(nif, supportsMulticast) && checkMethod(nif,isUp)) {
                Enumeration<InetAddress> addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // TODO: IpV6 support
                    if (!(addr instanceof Inet6Address)) {
                        ret.add(addr);
                    }
                }
            }
        }
        return ret;
    }

    // =======================================================================================================

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
        // Cannot check, hence we assume that is true
        return true;
    }

    /**
     * Get the local network info as a string
     *
     * @return return a description of the current network setup of the local host.
     *
     * @throws UnknownHostException
     * @throws SocketException
     */
    public static String dumpLocalNetworkInfo() throws UnknownHostException, SocketException {
        StringBuffer buffer = new StringBuffer();
        InetAddress addr = InetAddress.getLocalHost();
        buffer.append("Localhost: " + getAddrInfo(addr) + "\n");
        Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
        buffer.append("Network interfaces:\n");
        while (nifs.hasMoreElements()) {
            NetworkInterface nif = nifs.nextElement();
            buffer.append("  - " + getNetworkInterfaceInfo(nif) + "\n");
            Enumeration<InetAddress> addresses = nif.getInetAddresses();
            while (addresses.hasMoreElements()) {
                addr = addresses.nextElement();
                buffer.append("    " + getAddrInfo(addr) + "\n");
            }
        }
        return buffer.toString();
    }

    private static String getAddrInfo(InetAddress pAddr) throws SocketException {
        String ret = pAddr.getHostName() != null ? pAddr.getHostName() + " (" + pAddr.getHostAddress() + ")" : pAddr.getHostAddress();
        ret += " [site-local: " + pAddr.isSiteLocalAddress() + ", link-local: " + pAddr.isLinkLocalAddress() + ", lb: " + pAddr.isLoopbackAddress() + "]";
        NetworkInterface nif = NetworkInterface.getByInetAddress(pAddr);
        ret += " -- nif: " + getNetworkInterfaceInfo(nif);
        return ret;
    }

    private static String getNetworkInterfaceInfo(NetworkInterface pNif) throws SocketException {
        if (pNif == null) {
            return "[null]";
        }
        return pNif.getDisplayName() + " [up: " + pNif.isUp() + ", mc: " + pNif.supportsMulticast() +
               ", lb: " + pNif.isLoopback() + ", hw: " + formatHwAddress(pNif.getHardwareAddress()) + "]";
    }

    private static String formatHwAddress(byte[] pHardwareAddress) {
        if (pHardwareAddress == null) {
            return "[none]";
        }
        StringBuilder sb = new StringBuilder(18);
        for (byte b : pHardwareAddress) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
