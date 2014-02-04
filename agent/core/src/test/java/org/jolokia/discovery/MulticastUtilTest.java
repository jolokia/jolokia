package org.jolokia.discovery;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;

import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 04.02.14
 */
public class MulticastUtilTest {

    @Test
    public void createSocketNoAddress() throws IOException {
        InetAddress address = Inet6Address.getByName("fe80::e2f8:47ff:fe42:d872");
        MulticastUtil.newMulticastSocket(address);
    }

    @Test
    public void findLocalAddress() throws SocketException {
        Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
        System.out.println("IFs");
        boolean nonLoopback = false;
        while (ifs.hasMoreElements()) {
            NetworkInterface intf = ifs.nextElement();
            System.out.println(intf + " is loopback: " + intf.isLoopback());
            nonLoopback = nonLoopback || !intf.isLoopback();
        }
        InetAddress addr = MulticastUtil.findLocalAddressViaNetworkInterface();
        assertTrue(nonLoopback ? addr != null : addr == null);
        assertTrue(addr instanceof Inet4Address);
    }
}
