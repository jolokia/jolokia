package org.jolokia.agent.core.util;

import java.net.*;
import java.util.Enumeration;

import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 07.02.14
 */
public class NetworkUtilTest {

    @Test
    public void dump() throws SocketException, UnknownHostException {
        try {
            System.out.println(NetworkUtil.dumpLocalNetworkInfo());
        } catch (Exception exp) {
            exp.printStackTrace();
        }
    }

    @Test
    public void findLocalAddress() throws SocketException {
        Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
        System.out.println("IFs");
        boolean found = false;
        while (ifs.hasMoreElements()) {
            NetworkInterface intf = ifs.nextElement();
            System.out.println(intf + " is loopback: " + intf.isLoopback());
            found = found || (!intf.isLoopback() && intf.supportsMulticast() && intf.isUp());
        }
        InetAddress addr = NetworkUtil.findLocalAddressViaNetworkInterface();
        System.out.println("Address found via NIF: " + addr);
        assertTrue(found ? addr != null : addr == null);
        if (addr != null) {
            assertTrue(addr instanceof Inet4Address);
        }
    }
}
