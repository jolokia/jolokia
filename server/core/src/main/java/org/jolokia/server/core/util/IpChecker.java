package org.jolokia.server.core.util;

/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Checks whether a certain ip adresse is either equal another
 * address or falls within a subnet
 *
 * @author roland
 * @since Oct 8, 2009
 */

public final class IpChecker {

    private static int[] masks = new int[] {0x00, 0x80, 0xc0, 0xe0, 0xf0, 0xf8, 0xfc, 0xfe};

    private IpChecker() { }

    /**
     * Check whether a given IP Adress falls within a subnet or is equal to
     *
     * @param pExpected either a simple IP adress (without "/") or a net specification
     *        including a CIDR specification (e.g "/24" or "/255.255.255.0" (only for IPv4))
     * @param pToCheck the ip address to check
     * @return true if either the address to check is the same as the address expected
     *         of falls within the subnet if a netmask is given
     */
    public static boolean matches(String pExpected, String pToCheck) {
        boolean ipv6 = (pToCheck.startsWith("[") && pToCheck.endsWith("]")) || pToCheck.contains(":");
        if (ipv6 || pExpected.contains(":")) {
            return pExpected.contains(":") && matchesIPv6(pExpected, pToCheck);
        }

        String[] parts = pExpected.split("/",2);
        if (parts.length == 1) {
            // No Net part given, check for equality ...
            // Check for valid ips
            convertToIntTuple(pExpected);
            convertToIntTuple(pToCheck);
            return pExpected.equals(pToCheck);
        } else if (parts.length == 2) {
            int[] ipToCheck = convertToIntTuple(pToCheck);
            int[] ipPattern = convertToIntTuple(parts[0]);
            int[] netmask;
            if (parts[1].length() <= 2) {
                netmask = transformCidrToNetmask(parts[1]);
            } else {
                netmask = convertToIntTuple(parts[1]);
            }
            for (int i = 0; i<ipToCheck.length; i++) {
                if ((ipPattern[i] & netmask[i]) != (ipToCheck[i] & netmask[i])) {
                    return false;
                }
            }
            return true;
        } else {
            throw new IllegalArgumentException("Invalid IP adress specification " + pExpected);
        }
    }

    /**
     * Checks whether IPv6 address matches expected address or CIDR specification in {@code address/net-prefix} format.
     * @param pExpected
     * @param pToCheck
     * @return
     */
    public static boolean matchesIPv6(String pExpected, String pToCheck) {
        String[] parts = pExpected.split("/",2);
        if (parts.length == 1) {
            try {
                InetAddress expected = InetAddress.getByName(pExpected);
                InetAddress checked = InetAddress.getByName(pToCheck);
                return expected.equals(checked);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid IP adress specification " + pExpected);
            }
        } else {
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 128) {
                throw new IllegalArgumentException("Invalid IP adress specification " + pExpected);
            }
            try {
                InetAddress expected = InetAddress.getByName(parts[0]);
                InetAddress checked = InetAddress.getByName(pToCheck);
                byte[] e = expected.getAddress();
                byte[] c = checked.getAddress();
                int div = prefix / 8;
                for (int i = 0; i < div; i++) {
                    if (e[i] != c[i]) {
                        return false;
                    }
                }
                int rem = prefix % 8;
                if (rem != 0) {
                    // e.g. 13 means that first byte must be equal and 2nd byte's first 5 bits must match
                    return (e[div] & masks[rem]) == (c[div] & masks[rem]);
                }
                return true;
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid IP adress specification " + pExpected);
            }
        }
    }

    private static int[] transformCidrToNetmask(String pCidrString) {
        try {
            int pCidr = Integer.parseInt(pCidrString);
            if (pCidr < 0 || pCidr > 32) {
                throw new IllegalArgumentException("Invalid netmask specification " + pCidr);
            }
            StringBuilder buf = new StringBuilder();
            buf.append("1".repeat(pCidr));
            buf.append("0".repeat(32 - pCidr));
            int[] ret = new int[4];
            int start = 0,end = 8;

            for (int j=0;j<4;j++) {
                ret[j] = (Integer.parseInt(buf.substring(start,end), 2));
                start += 8; end += 8;
            }
            return ret;
        } catch (NumberFormatException exp) {
            throw new IllegalArgumentException("Invalid netmask specification " + pCidrString,exp);
        }
    }


    private static int[] convertToIntTuple(String pAddress) {
        String[] parts = pAddress.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IP-Adresse " + pAddress);
        }
        int[] ret = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                ret[i] = Integer.parseInt(parts[i]);
                if (ret[i] < 0 || ret[i] > 255) {
                    throw new IllegalArgumentException("Invalid IP-Adresse " + pAddress);
                }
            } catch (NumberFormatException exp) {
                throw new IllegalArgumentException("Invalid IP-Adresse " + pAddress,exp);
            }
        }
        return ret;
    }
}
