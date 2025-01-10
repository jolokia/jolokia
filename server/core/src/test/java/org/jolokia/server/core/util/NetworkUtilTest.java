package org.jolokia.server.core.util;

import java.net.*;
import java.util.Enumeration;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 07.02.14
 */
public class NetworkUtilTest {

    @Test
    public void dump() {
        try {
            System.out.println(NetworkUtil.dumpLocalNetworkInfo());
        } catch (Exception exp) {
            System.out.println(exp.getMessage());
        }
    }

    @Test
    public void findLocalIP4Address() throws SocketException {
        Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
        System.out.println("IFs");
        boolean found = false;
        while (ifs.hasMoreElements()) {
            NetworkInterface intf = ifs.nextElement();
            System.out.println(intf + " is loopback: " + intf.isLoopback());
            found = found || (!intf.isLoopback() && intf.supportsMulticast() && intf.isUp());
        }
        InetAddress addr = NetworkUtil.findLocalAddressViaNetworkInterface(Inet4Address.class);
        System.out.println("Address found via NIF: " + addr);
        assertEquals(addr != null, found);
        if (addr != null) {
            assertTrue(addr instanceof Inet4Address);
        }
    }

    @Test
    public void ip6Support() {
        boolean preferIP4Stack = Boolean.getBoolean("java.net.preferIPv4Stack");
        boolean preferIP6Addresses = Boolean.getBoolean("java.net.preferIPv6Addresses");
        assertEquals(NetworkUtil.isIPv6Supported(), !preferIP4Stack);
    }

    @Test
    public void findLocalIP6Address() throws SocketException {
        InetAddress addr = NetworkUtil.findLocalAddressViaNetworkInterface(Inet6Address.class);
        System.out.println("Address found via NIF: " + addr);
        assertTrue(addr instanceof Inet6Address);
    }

    @Test
    public void replaceExpression() throws SocketException, UnknownHostException {
        String host = NetworkUtil.getLocalAddress().getHostName();
        String ip = NetworkUtil.getLocalAddress().getHostAddress();
        System.getProperties().setProperty("test.prop", "testy");
        System.getProperties().setProperty("test2:prop", "testx");
        String[] testData = {
                "$host",host,
                "bla ${host} blub","bla " + host + " blub",
                "$ip$host",ip + host,
                "${ ip     }",ip,
                "|${prop:test.prop}|","|testy|",
                "${prop:test2:prop}","testx"
        };
        for (int i = 0; i < testData.length; i+=2) {
            assertEquals(NetworkUtil.replaceExpression(testData[i]),testData[i+1],"Checking " + testData[i]);
        }
    }

    @Test
    public void replaceExpressionWithEnv() {
        if (!System.getProperty("os.name").startsWith("Windows")) {
            String path = NetworkUtil.replaceExpression("Hello ${EnV:PATH} World");
            assertTrue(path.contains("bin"));
            assertTrue(path.startsWith("Hello"));
            assertTrue(path.endsWith("World"));
        }
    }

    @Test
    public void invalidExpression() {
        String[] testData = {
                "$unknown",
                "$hostunknown",
                "${prop: with space}"
        };
        for (String t : testData) {
            try {
                NetworkUtil.replaceExpression(t);
                fail(t+ " has been parsed");
            } catch (IllegalArgumentException exp) {
                // Ok.
            }
        }
    }
}
