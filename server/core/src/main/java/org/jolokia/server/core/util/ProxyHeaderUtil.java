/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.server.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class to process proxy-related headers: <ul>
 *     <li>RFC 7239 {@code Forwarded}</li>
 *     <li>De-facto standard {@code X-Forwarded-*}</li>
 *     <li>{@code X-Real-IP}</li>
 * </ul>
 */
public class ProxyHeaderUtil {

    /**
     * Knowing the incoming information about host addresses (direct peer address and values passed using proxy
     * headers), return an array of addresses that will be validated if they're trusted. The order to validate is:
     * <ul>
     *     <li>The incoming address (client or direct reverse proxy) - always returned as first</li>
     *     <li>List of addresses from {@code Forwarded} header (standard RFC 7239)</li>
     *     <li>List of addresses from {@code X-Forwarded-For} header (de-facto standard) - if no {@code Forwarded}</li>
     *     <li>Address from {@code X-Real-IP} header if no lists from the previous headers</li>
     * </ul>
     * Additionally, if {@code trustProxyHeaders} is true (which is not default), only the first value from
     * {@code Forwarded}/{@code X-Forwarded-For} will be checked, assuming that some trusted proxy actually set proper
     * value.
     *
     * @param trustProxyHeaders
     * @param peerAddress
     * @param realIp
     * @param forwardedFor
     * @param forwarded
     * @return
     */
    public static String[] addressChain(boolean trustProxyHeaders, String peerAddress, String realIp, String forwardedFor, String forwarded) {
        Set<String> addresses = new LinkedHashSet<>();

        if (forwarded != null && !forwarded.isEmpty()) {
            addresses.addAll(parseRFC7239Header(trustProxyHeaders, forwarded));
        } else if (forwardedFor != null && !forwardedFor.isEmpty()) {
            addresses.addAll(parseXForwardedForHeader(trustProxyHeaders, forwardedFor));
        } else if (realIp != null && !realIp.isEmpty()) {
            addresses.add(realIp);
        }
        // this may be a peer address of a reverse proxy
        addresses.add(peerAddress);

        return addresses.toArray(new String[0]);
    }

    private static Collection<String> parseRFC7239Header(boolean trustProxyHeaders, String forwarded) {
        if (forwarded == null || forwarded.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> addresses = new ArrayList<>();

        String[] tab1 = forwarded.split(",");
        for (String value1 : tab1) {
            String[] tab2 = value1.split(";");
            for (String value2 : tab2) {
                String[] tab3 = value2.split("=", 2);
                if (tab3.length == 2 && "for".equalsIgnoreCase(tab3[0].trim())) {
                    String address = tab3[1].trim();
                    if (address.length() >= 2 && address.charAt(0) == '\"' && address.charAt(address.length() - 1) == '\"') {
                        address = address.substring(1, address.length() - 1);
                    }
                    if (address.startsWith("[")) {
                        // ip6 - with or without port
                        int properPortIdx = address.indexOf("]:");
                        if (properPortIdx != -1) {
                            address = address.substring(1, properPortIdx);
                        } else if (address.endsWith("]")) {
                            address = address.substring(1, address.length() - 1);
                        }
                    } else {
                        // ip4 - with or without port
                        int colon = address.indexOf(':');
                        if (colon != -1) {
                            address = address.substring(0, colon);
                        }
                    }
                    // whatever is left - even if not valid, we'll return. ip checker will fail with illegal value
                    addresses.add(address);
                    if (trustProxyHeaders) {
                        return addresses;
                    }
                }
            }
        }

        return addresses;
    }

    private static Collection<String> parseXForwardedForHeader(boolean trustProxyHeaders, String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> addresses = new ArrayList<>();

        String[] tab1 = forwardedFor.split(",");
        for (String address : tab1) {
            address = address.trim();
            if (address.isEmpty()) {
                continue;
            }
            if (address.length() >= 2 && address.charAt(0) == '\"' && address.charAt(address.length() - 1) == '\"') {
                address = address.substring(1, address.length() - 1);
            }
            if (address.startsWith("[")) {
                // ip6 - with or without port
                int properPortIdx = address.indexOf("]:");
                if (properPortIdx != -1) {
                    address = address.substring(1, properPortIdx);
                } else if (address.endsWith("]")) {
                    address = address.substring(1, address.length() - 1);
                }
            } else {
                // ip4 - with or without port
                int colon = address.indexOf(':');
                if (colon != -1) {
                    address = address.substring(0, colon);
                }
            }
            // whatever is left - even if not valid, we'll return. ip checker will fail with illegal value
            addresses.add(address);
            if (trustProxyHeaders) {
                break;
            }
        }

        return addresses;
    }

}
