package org.jolokia.util;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for network related stuff
 *
 * @author roland
 * @since 05.02.14
 */
public final class NetworkUtil {

    // Only available for Java 6
    private static Method isUp;
    private static Method supportsMulticast;

    static {
        // Check for JDK method  d which are available only for JDK6
        try {
            isUp = NetworkInterface.class.getMethod("isUp", (Class<?>[]) null);
            supportsMulticast = NetworkInterface.class.getMethod("supportsMulticast", (Class<?>[]) null);
        } catch (NoSuchMethodException e) {
            isUp = null;
            supportsMulticast = null;
        }
    }

    // Utility class
    private NetworkUtil() {
    }

    // Debug info
    public static void main(String[] args) throws UnknownHostException, SocketException {
        System.out.println(dumpLocalNetworkInfo()); // NOSONAR
    }

    /**
     * Get a local, IP4 Address, preferable a non-loopback address which is bound to an interface.
     *
     * @return
     * @throws UnknownHostException
     * @throws SocketException
     */
    public static InetAddress getLocalAddress() throws UnknownHostException, SocketException {
        InetAddress addr = InetAddress.getLocalHost();
        NetworkInterface nif = NetworkInterface.getByInetAddress(addr);
        if (addr.isLoopbackAddress() || addr instanceof Inet6Address || nif == null) {
            // Find local address that isn't a loopback address
            InetAddress lookedUpAddr = findLocalAddressViaNetworkInterface();
            // If a local, multicast enabled address can be found, use it. Otherwise
            // we are using the local address, which might not be what you want
            addr = lookedUpAddr != null ? lookedUpAddr : InetAddress.getByName("127.0.0.1");
        }
        return addr;
    }

    /**
     * Get a local address which supports multicast. A loopback adress is returned, but only if not
     * another is available
     *
     * @return a multicast enabled address of null if none could be found
     * @throws UnknownHostException
     * @throws SocketException
     */
    public static InetAddress getLocalAddressWithMulticast() throws UnknownHostException, SocketException {
        InetAddress addr = InetAddress.getLocalHost();
        NetworkInterface nif = NetworkInterface.getByInetAddress(addr);
        if (addr.isLoopbackAddress() || addr instanceof Inet6Address || !isMulticastSupported(nif)) {
            // Find local address that isn't a loopback address
            InetAddress lookedUpAddr = findLocalAddressViaNetworkInterface();
            // If a local, multicast enabled address can be found, use it. Otherwise
            // we are using the local address, which might not be what you want
            if (lookedUpAddr != null) {
                return lookedUpAddr;
            }
            addr = InetAddress.getByName("127.0.0.1");
        }
        if (isMulticastSupported(addr)) {
            return addr;
        } else {
            throw new UnknownHostException("Cannot find address of local host which can be used for multicasting");
        }
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
            for (Enumeration<InetAddress> addrEnum = nif.getInetAddresses(); addrEnum.hasMoreElements(); ) {
                InetAddress interfaceAddress = addrEnum.nextElement();
                if (useInetAddress(nif, interfaceAddress)) {
                    return interfaceAddress;
                }
            }
        }
        return null;
    }


    /**
     * Check, whether multicast is supported at all by at least one interface
     *
     * @return true if at least one network interface supports multicast
     */
    public static boolean isMulticastSupported() throws SocketException {
        return getMulticastAddresses().size() != 0;
    }

    /**
     * Check whether the given interface supports multicast and is up
     *
     * @param pNif check whether the given interface supports multicast
     * @return true if multicast is supported and the interface is up
     */
    public static boolean isMulticastSupported(NetworkInterface pNif) {
        return pNif != null && checkMethod(pNif, isUp) && checkMethod(pNif, supportsMulticast);
    }

    /**
     * Check whether the given address' interface supports multicast
     *
     * @param pAddr address to check
     * @return true if the underlying networkinterface is up and supports multicast
     * @throws SocketException
     */
    public static boolean isMulticastSupported(InetAddress pAddr) throws SocketException {
        return isMulticastSupported(NetworkInterface.getByInetAddress(pAddr));
    }

    /**
     * Get all local addresses on which a multicast can be send
     *
     * @return list of all multi cast capable addresses
     */
    public static List<InetAddress> getMulticastAddresses() throws SocketException {
        Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
        List<InetAddress> ret = new ArrayList<InetAddress>();
        while (nifs.hasMoreElements()) {
            NetworkInterface nif = nifs.nextElement();
            if (checkMethod(nif, supportsMulticast) && checkMethod(nif, isUp)) {
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

    public static String getAgentId(int objectId, String type) {
        String address;
        try {
            address = getLocalAddress().getHostAddress();
        } catch (IOException exp) {
            address = "local";
        }
        return address + "-" + getProcessId() + "-" + Integer.toHexString(objectId) + "-" + type;
    }

    /**
     * Examine the given URL and replace the host with a non-loopback host if possible. It is checked,
     * whether the port is open as well.
     * <p/>
     * A replaced host uses the  IP address instead of a (possibly non resolvable) name.
     *
     * @param pRequestURL url to examine and to update
     * @return the 'sane' URL (or the original one if no san
     */
    public static String sanitizeLocalUrl(String pRequestURL) {
        try {
            URL url = new URL(pRequestURL);
            String host = url.getHost();
            int port = getPort(url);
            InetAddress address = findLocalAddressListeningOnPort(host, port);
            return new URL(url.getProtocol(), address.getHostAddress(), port, url.getFile()).toExternalForm();
        } catch (IOException e) {
            // Best effort, we at least tried it
            return pRequestURL;
        }
    }

    private static int getPort(URL url) {
        int port = url.getPort();
        if (port != -1) {
            return port;
        }
        // Return default ports
        return url.getProtocol().equalsIgnoreCase("https") ? 443 : 80;
    }

    // =======================================================================================================

    // Only use the given interface on the given network interface if it is up and supports multicast
    private static boolean useInetAddress(NetworkInterface networkInterface, InetAddress interfaceAddress) {
        return checkMethod(networkInterface, isUp) &&
               checkMethod(networkInterface, supportsMulticast) &&
               // TODO: IpV6 support
               !(interfaceAddress instanceof Inet6Address) &&
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

    // Check for an non-loopback, local adress listening on the given port
    private static InetAddress findLocalAddressListeningOnPort(String pHost, int pPort) throws UnknownHostException, SocketException {
        InetAddress address = InetAddress.getByName(pHost);
        if (address.isLoopbackAddress()) {
            // First check local address
            InetAddress localAddress = getLocalAddress();
            if (!localAddress.isLoopbackAddress() && isPortOpen(localAddress, pPort)) {
                return localAddress;
            }

            // Then try all addresses attache to all interfaces
            localAddress = getLocalAddressFromNetworkInterfacesListeningOnPort(pPort);
            if (localAddress != null) {
                return localAddress;
            }
        }
        return address;
    }

    private static InetAddress getLocalAddressFromNetworkInterfacesListeningOnPort(int pPort) {
        try {
            Enumeration<NetworkInterface> networkInterfaces;
            networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface nif = networkInterfaces.nextElement();
                for (Enumeration<InetAddress> addrEnum = nif.getInetAddresses(); addrEnum.hasMoreElements(); ) {
                    InetAddress interfaceAddress = addrEnum.nextElement();
                    if (!interfaceAddress.isLoopbackAddress() && checkMethod(nif, isUp) && isPortOpen(interfaceAddress, pPort)) {
                        return interfaceAddress;
                    }
                }
            }
        } catch (SocketException e) {
            return null;
        }
        return null;
    }

    // Check a port by connecting to it. Try only 200ms.
    private static boolean isPortOpen(InetAddress pAddress, int pPort) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.setReuseAddress(true);
            SocketAddress sa = new InetSocketAddress(pAddress, pPort);
            socket.connect(sa, 200);
            return socket.isConnected();
        } catch (IOException e) {
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Best effort. Hate that close throws an IOException, btw. Never saw a real use case for that.
                }
            }
        }
    }

    // Hack for finding the process id. Used in creating an unique agent id.
    private static String getProcessId() {
        // something like '<pid>@<hostname>', at least in SUN / Oracle JVMs
        final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        final int index = jvmName.indexOf('@');
        return index < 0 ? jvmName : jvmName.substring(0, index);
    }

    /**
     * Get the local network info as a string
     *
     * @return return a description of the current network setup of the local host.
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

    private static final Pattern EXPRESSION_EXTRACTOR = Pattern.compile("\\$\\{?\\s*([\\w:-_.]+)\\s*}?");

    /**
     * Replace expression ${host} and ${ip} with the localhost name or IP in the given string.
     * In addition the notation ${env:ENV_VAR} and ${prop:sysprop} can be used to refer to environment
     * and system properties respectively.
     *
     * @param pValue value to examine
     * @return the value with the variables replaced.
     * @throws IllegalArgumentException when the expression is unknown or an error occurs when extracting the host name
     */
    public static String replaceExpression(String pValue) {
        if (pValue == null) {
            return null;
        }
        Matcher matcher = EXPRESSION_EXTRACTOR.matcher(pValue);
        StringBuffer ret = new StringBuffer();
        try {
            while (matcher.find()) {
                String var = matcher.group(1);
                String value;
                if (var.equalsIgnoreCase("host")) {
                    value = getLocalAddress().getHostName();
                } else if (var.equalsIgnoreCase("ip")) {
                    value = getLocalAddress().getHostAddress();
                } else {
                    String key = extractKey(var,"env");
                    if (key != null)  {
                        value = System.getenv(key);
                    } else {
                        key = extractKey(var,"prop");
                        if (key != null) {
                            value = System.getProperty(key);
                        } else {
                            throw new IllegalArgumentException("Unknown expression " + var + " in " + pValue);
                        }
                    }
                }
                matcher.appendReplacement(ret, value != null ? value.trim() : null);
            }
            matcher.appendTail(ret);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot lookup host" + e, e);
        }
        return ret.toString();
    }

    private static String extractKey(String pVar, String pPrefix) {
        if (pVar.toLowerCase().startsWith(pPrefix + ":")) {
            String ret = pVar.substring(pPrefix.length() + 1);
            if (ret.length() == 0) {
                throw new IllegalArgumentException("Expression with " + pPrefix + ": must not contain spaces");
            }
            return ret;
        }
        return null;
    }

    // ==============================================================================================================================================
    // Dump methods

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
