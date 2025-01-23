package org.jolokia.service.discovery;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;

import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.impl.QuietLogHandler;
import org.jolokia.server.core.util.NetworkUtil;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.testng.annotations.Test;

import static org.testng.Assert.fail;

/**
 * @author roland
 * @since 04.02.14
 */
public class MulticastUtilTest {

    @Test
    public void createSocketNoAddress() throws IOException {
        if (!NetworkUtil.isIPv6Supported()) {
            return;
        }
        InetAddress address = NetworkUtil.getLocalAddress(Inet6Address.class);
        JolokiaContext ctx =
            new TestJolokiaContext.Builder()
                .logHandler(new QuietLogHandler())
                .build();
        if (NetworkUtil.isMulticastSupported()) {
            MulticastUtil.newMulticastSocket(address, ctx);
        } else {
            try {
                MulticastUtil.newMulticastSocket(address, ctx);
                fail();
            } catch (IOException exp) {
                // Expected since no multicast socket is available
            }
        }
    }
}
