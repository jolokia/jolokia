package org.jolokia.server.core.util;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for network related stuff
 *
 * @author roland
 * @since 05.02.14
 */
public final class NetworkUtil {

    // Utility class
    private NetworkUtil() {
    }

    // Debug info
    public static void main(String[] args) throws UnknownHostException, SocketException {
        System.out.println(dumpLocalNetworkInfo()); // NOSONAR
    }

    /**
     * Get {@link InetAddress} representing <em>any</em> address ({@code 0.0.0.0} on IPv4 or {@code [::]} on IPv6).
     *
     * @return
     */
    public static InetAddress getAnyAddress() {
        try {
            return isIPv6Supported() ? Inet6Address.getByName("::") : InetAddress.getByAddress(new byte[]{0, 0, 0, 0});
        } catch (UnknownHostException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Get a local, IP4 Address, preferable a non-loopback address which is bound to a physical interface.
     *
     * @return
     * @throws UnknownHostException
     * @throws SocketException
     */
    public static InetAddress getLocalAddress() throws UnknownHostException, SocketException {
        return getLocalAddress(Inet4Address.class);
    }

    /**
     * <p>Get a local, preferable a non-loopback address which is bound to a physical interface.
     * Type of the address is specified by {@code type} parameter.</p>
     * <p>But if no real IP address can be found, loopback address of relevant type is returned ({@code 127.0.0.1} or
     * {@code ::1}). In this case it's rather not Multicast enabled.</p>
     *
     * @param type A type of address to use (IPv4 or IPv6)
     * @return
     * @throws UnknownHostException
     * @throws SocketException
     */
    public static InetAddress getLocalAddress(Class<? extends InetAddress> type) throws UnknownHostException, SocketException {
        // getLocalHost tries to resolve local hostname as returned by gethostname (unistd.h)
        InetAddress addr = InetAddress.getLocalHost();
        NetworkInterface nif = NetworkInterface.getByInetAddress(addr);
        if (addr.isLoopbackAddress() || addr.getClass() != type || nif == null) {
            // Find local address that isn't a loopback address and is of desired class
            InetAddress lookedUpAddr = findLocalAddressViaNetworkInterface(type);
            // If a local, multicast enabled address can be found, use it. Otherwise
            // we are using the local address, which might not be what you want
            if (lookedUpAddr != null) {
                addr = lookedUpAddr;
            } else {
                if (type == null || type == Inet4Address.class) {
                    addr = InetAddress.getByName("127.0.0.1");
                } else {
                    addr = InetAddress.getByName("::1");
                }
            }
        }
        return addr;
    }

    /**
     * Get a local address which supports multicast. Loopback address is never returned, an exception is thrown
     * instead.
     *
     * @param type A type of address to use (IPv4 or IPv6)
     * @return a multicast enabled address if available
     * @throws UnknownHostException if we can't find non-loopback, multicast-enabled address.
     * @throws SocketException
     */
    public static InetAddress getLocalAddressWithMulticast(Class<? extends InetAddress> type) throws UnknownHostException, SocketException {
        InetAddress addr = getLocalAddress(type);
        if (isMulticastSupported(addr)) {
            return addr;
        } else {
            throw new UnknownHostException("Cannot find address of local host which can be used for multicasting");
        }
    }

    /**
     * Get an address of desired type (IPv4 or IPv6) using available network interfaces.
     * The returned address is not loopback, is active (<em>up</em>) and supports (UDP) multicast. Preferably the
     * address is associated with physical network interface (with hardware address) instead of a virtual one
     * (bridge, VPN, ...).
     *
     * @return
     * @throws SocketException
     */
    public static InetAddress findLocalAddressViaNetworkInterface(Class<? extends InetAddress> type) throws SocketException {
        Enumeration<NetworkInterface> networkInterfaces;
        try {
            networkInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return null;
        }

        InetAddress fallback = null;
        InetAddress fallbackHardware = null;
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface nif = networkInterfaces.nextElement();
            for (Enumeration<InetAddress> addrEnum = nif.getInetAddresses(); addrEnum.hasMoreElements(); ) {
                InetAddress interfaceAddress = addrEnum.nextElement();
                if (useInetAddress(nif, interfaceAddress, type)) {
                    // always prefer "proper" address, which is non-site and non-link and also which is related
                    // to an interface with hardware address (so we prefer ethernet card over VPN interface
                    // for example)
                    // site is:
                    //  - ip4: 10.0.0.0/8, 172.16.0.0/12 or 192.168.0.0/16
                    //  - ip6: fec0::
                    // link is:
                    //  - ip4: 169.254.0.0/16
                    //  - ip6: fe80::
                    if (nif.getHardwareAddress() != null) {
                        // we use real interface
                        if (fallbackHardware == null) {
                            fallbackHardware = interfaceAddress;
                        } else {
                            if ((fallbackHardware.isLinkLocalAddress() || fallbackHardware.isSiteLocalAddress())
                            && !(interfaceAddress.isLinkLocalAddress() || interfaceAddress.isSiteLocalAddress())) {
                                fallbackHardware = interfaceAddress;
                            }
                        }
                    } else {
                        // we use virtual (e.g., VPN, bridge) interface
                        if (fallback == null) {
                            fallback = interfaceAddress;
                        } else {
                            if ((fallback.isLinkLocalAddress() || fallback.isSiteLocalAddress())
                                && !(interfaceAddress.isLinkLocalAddress() || interfaceAddress.isSiteLocalAddress())) {
                                fallback = interfaceAddress;
                            }
                        }
                    }
                }
            }
        }

        return fallbackHardware != null ? fallbackHardware : fallback;
    }

    /**
     * Check, whether multicast is supported at all by at least one interface
     *
     * @return true if at least one network interface supports multicast
     */
    public static boolean isMulticastSupported() {
        return !getMulticastAddresses().isEmpty();
    }

    /**
     * Not very clever way to check if IPv6 is supported.
     * @return
     */
    public static boolean isIPv6Supported() {
        // among others, Java (native code) checks /proc/net/if_inet6 file and
        // JVM_FindLibraryEntry(RTLD_DEFAULT, "inet_pton") API availability
        boolean preferIP4Stack = Boolean.getBoolean("java.net.preferIPv4Stack");
        if (preferIP4Stack) {
            // even if technically it may be supported
            return false;
        }

        boolean preferIP6Addresses = Boolean.getBoolean("java.net.preferIPv6Addresses");
        if (preferIP6Addresses) {
            // we should get IPv6 ::1 address here
            // because on IPv4 we'd use java.net.InetAddress.impl == java.net.Inet4AddressImpl
            InetAddress lo = InetAddress.getLoopbackAddress();
            return lo instanceof Inet6Address;
        } else {
            // even on IPv6 we'll get 127.0.0.1 here, so we have to check if any interface has IPv6 address assigned
            try {
                // this is fine also when IPv6 is actually supported, but java.net.preferIPv4Stack=true
                NetworkInterface nif = NetworkInterface.getByInetAddress(InetAddress.getByName("::1"));
                return nif != null;
            } catch (SocketException | UnknownHostException e) {
                return false;
            }
        }
    }

    /**
     * Check whether the given interface supports multicast and is up
     *
     * @param pNif check whether the given interface supports multicast
     * @return true if multicast is supported and the interface is up
     */
    public static boolean isMulticastSupported(NetworkInterface pNif) throws SocketException {
        return pNif != null && pNif.isUp() && pNif.supportsMulticast();
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
     * Get all local addresses on which a multicast can be send - whether IPv4 or IPv6. Each {@link InetAddress}
     * is associated with relevant {@link NetworkInterface}.
     *
     * @return list of all multi cast capable addresses
     */
    public static List<NetworkInterfaceAndAddress> getMulticastAddresses() {
        Enumeration<NetworkInterface> nifs;
        try {
            nifs = NetworkInterface.getNetworkInterfaces();
            List<NetworkInterfaceAndAddress> ret = new ArrayList<>();
            while (nifs.hasMoreElements()) {
                NetworkInterface nif = nifs.nextElement();
                if (nif.supportsMulticast() && nif.isUp()) {
                    Enumeration<InetAddress> addresses = nif.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        ret.add(new NetworkInterfaceAndAddress(nif, addresses.nextElement()));
                    }
                }
            }
            return ret;
        } catch (SocketException exp) {
            return Collections.emptyList();
        }
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
     * <p>
     * A replaced host uses the  IP address instead of a (possibly non resolvable) name.
     *
     * @param pRequestURL url to examine and to update
     * @return the 'sane' URL (or the original one if no sane address can be found)
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

    /**
     * Checks whether given address is of supported type, is active (up), is not loopback address and whether
     * it's supporting UDP multicast.
     * @param networkInterface
     * @param interfaceAddress
     * @param type
     * @return
     * @throws SocketException
     */
    private static boolean useInetAddress(NetworkInterface networkInterface, InetAddress interfaceAddress,
                                          Class<? extends InetAddress> type) throws SocketException {
        return networkInterface.isUp() && networkInterface.supportsMulticast() &&
            interfaceAddress.getClass() == type &&
            !interfaceAddress.isLoopbackAddress();
    }

    // Check for an non-loopback, local adress listening on the given port
    private static InetAddress findLocalAddressListeningOnPort(String pHost, int pPort) throws UnknownHostException, SocketException {
        InetAddress address = InetAddress.getByName(pHost);
        if (address.isLoopbackAddress()) {
            // First check local address
            InetAddress localAddress = getLocalAddress(address.getClass());
            if (!localAddress.isLoopbackAddress() && isPortOpen(localAddress, pPort)) {
                return localAddress;
            }

            // Then try all addresses attached to all interfaces
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
                    if (!interfaceAddress.isLoopbackAddress() && nif.isUp() && isPortOpen(interfaceAddress, pPort)) {
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
        try (Socket socket = new Socket()) {
            socket.setReuseAddress(true);
            SocketAddress sa = new InetSocketAddress(pAddress, pPort);
            socket.connect(sa, 200);
            return socket.isConnected();
        } catch (IOException e) {
            return false;
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
        StringBuilder buffer = new StringBuilder();
        InetAddress addr = InetAddress.getLocalHost();
        buffer.append("Localhost: ").append(getAddrInfo(addr)).append("\n");
        Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
        buffer.append("Network interfaces:\n");
        while (nifs.hasMoreElements()) {
            NetworkInterface nif = nifs.nextElement();
            buffer.append("  - ").append(getNetworkInterfaceInfo(nif)).append("\n");
            Enumeration<InetAddress> addresses = nif.getInetAddresses();
            while (addresses.hasMoreElements()) {
                addr = addresses.nextElement();
                buffer.append("    ").append(getAddrInfo(addr)).append("\n");
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
        StringBuilder ret = new StringBuilder();
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
                matcher.appendReplacement(ret, value != null ? value.trim() : "");
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
            if (ret.isEmpty()) {
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
