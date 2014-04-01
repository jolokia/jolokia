package org.jolokia.discovery;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;

import org.jolokia.util.LogHandler;
import org.jolokia.util.NetworkUtil;
import org.testng.annotations.Test;

import static org.testng.Assert.fail;

/**
 * @author roland
 * @since 04.02.14
 */
public class MulticastUtilTest {

    @Test
    public void createSocketNoAddress() throws IOException {
        InetAddress address = Inet6Address.getByName("fe80::e2f8:47ff:fe42:d872");
        if (NetworkUtil.isMulticastSupported()) {
            MulticastUtil.newMulticastSocket(address, LogHandler.QUIET);
        } else {
            try {
                MulticastUtil.newMulticastSocket(address, LogHandler.QUIET);
                fail();
            } catch (IOException exp) {
                // Expected since no multicast socket is available
            }
        }
    }
}
