package org.jolokia.config.address;

/*
 * 
 * Copyright 2020 Georg Tsakumagos
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
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Basic testing functions for Implementations of {@link AddressConfigService}
 * 
 * @author Georg Tsakumagos
 * @since 21.06.2020
 *
 */
public abstract class AddressConfigServiceTstBase {

    public static final String ASSERT_NOTNULL_REF = "The returned reference should not be null.";
    public static final String ASSERT_NULL_REF = "The returned reference should be null.";
    
    /**
     * Bad Reg-Ex-Pattern: Unclosed Group
     */
    public static final String PATTERN_BAD = "(.*";
    /**
     * Pattern that should not match any NicName
     */
    public static final String PATTERN_NEVERLAND = "\\W{666}";
    
    /**
     * Lookup the first Interface having a name and an ip address.
     * 
     * @return The interface. There should be one at least nowadays.
     * @throws SocketException If something went wrong.
     */
    public NetworkInterface getFirstNamedInterfaceWithIP() throws SocketException {

        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();

        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();

            if (null != nic.getName() && nic.getName().length() > 0) {
                Enumeration<InetAddress> addresses = nic.getInetAddresses();

                while (addresses.hasMoreElements()) {
                    return nic;
                }
            }
        }
        return null;
    }

    /**
     * Lookup the first ip address.
     * 
     * @return The ip. There should be one at least nowadays.
     * @throws SocketException If something went wrong.
     */
    public InetAddress getInterfaceIP() throws SocketException {

        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();

        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();

            if (null != nic.getName() && nic.getName().length() > 0) {
                Enumeration<InetAddress> addresses = nic.getInetAddresses();

                while (addresses.hasMoreElements()) {
                    return addresses.nextElement();
                }
            }
        }
        return null;
    }
    
    /**
     * Create a new configuration.
     * 
     * @return The configuration.
     */
    public Map<String, String> newAgentConfig() {
        return new HashMap<String, String>();
    }
}
