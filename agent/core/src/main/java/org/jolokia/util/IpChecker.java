package org.jolokia.util;

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


/**
 * Checks whether a certain ip adresse is either equal another
 * address or falls within a subnet
 *
 * @author roland
 * @since Oct 8, 2009
 */

public final class IpChecker {

    private IpChecker() { }

    /**
     * Check whether a given IP Adress falls within a subnet or is equal to
     *
     * @param pExpected either a simple IP adress (without "/") or a net specification
     *        including a netmask (e.g "/24" or "/255.255.255.0")
     * @param pToCheck the ip address to check
     * @return true if either the address to check is the same as the address expected
     *         of falls within the subnet if a netmask is given
     */
    public static boolean matches(String pExpected, String pToCheck) {
        String[] parts = pExpected.split("/",2);
        if (parts.length == 1) {
            // No Net part given, check for equality ...
            // Check for valid ips
            convertToIntTuple(pExpected);
            convertToIntTuple(pToCheck);
            return pExpected.equals(pToCheck);
        } else if (parts.length == 2) {
            int ipToCheck[] = convertToIntTuple(pToCheck);
            int ipPattern[] = convertToIntTuple(parts[0]);
            int netmask[];
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

	private static int[] transformCidrToNetmask(String pCidrString) {
        try {
            int pCidr = Integer.parseInt(pCidrString);
            if (pCidr < 0 || pCidr > 32) {
                throw new IllegalArgumentException("Invalid netmask specification " + pCidr);
            }
            StringBuffer buf = new StringBuffer();
            for (int i=0;i<pCidr;i++) {
                buf.append("1");
            }
            for (int i=pCidr;i<32;i++) {
                buf.append("0");
            }
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
        int ret[] = new int[4];
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
