package org.jolokia.discovery;

import java.io.IOException;
import java.net.*;

import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
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
        NetworkInterface.getNetworkInterfaces();
        InetAddress addr = MulticastUtil.findLocalAddressViaNetworkInterface();
        assertNotNull(addr);
        assertTrue(addr instanceof Inet4Address);
    }
}
