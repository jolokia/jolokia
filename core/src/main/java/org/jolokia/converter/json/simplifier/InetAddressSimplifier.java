/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.converter.json.simplifier;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

public class InetAddressSimplifier extends SimplifierAccessor<InetAddress> {

    public InetAddressSimplifier() {
        super(InetAddress.class);

        addExtractor("address", new AddressAttributeExtractor());
    }

    @Override
    public String extractString(Object pValue) {
        InetAddress address = (InetAddress) pValue;

        // only canonical form. no toString() with host address - even with allowDnsReverseLookup
        if (address instanceof Inet4Address) {
            return address.getHostAddress();
        } else if (address instanceof Inet6Address) {
            String ip6 = address.getHostAddress();
            int percent = ip6.indexOf('%');
            if (percent > 0) {
                // skip scope ifname/id
                return ip6.substring(0, percent);
            }
            return ip6;
        } else {
            // maybe in 20 years?
            throw new UnsupportedOperationException("InetAddress class not supported:" + address.getClass().getName());
        }
    }

    private class AddressAttributeExtractor implements AttributeExtractor<InetAddress> {
        @Override
        public Object extract(InetAddress address) {
            return extractString(address);
        }
    }

}
