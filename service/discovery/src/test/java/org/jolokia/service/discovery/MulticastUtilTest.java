package org.jolokia.service.discovery;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;

import org.testng.annotations.Test;

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

}
