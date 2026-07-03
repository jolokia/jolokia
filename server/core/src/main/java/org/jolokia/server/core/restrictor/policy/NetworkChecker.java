package org.jolokia.server.core.restrictor.policy;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;


import org.jolokia.server.core.util.IpChecker;
import org.w3c.dom.*;

/**
 * Check whether a host is allowed to access the agent. The restriction
 * can be specified in the policy file with the <code>&lt;remote&gt;</code>
 * tag. Either plain host or subnet (in the CIDR notation) can be specified
 * <br>
 * Example:
 * <pre>
 * &lt;remote&gt;
 *   &lt;host&gt;planck&lt;/host&gt;
 *   &lt;host&gt;10.0.11.125&lt;/host&gt;
 *   &lt;host&gt;11.0.0.0/16&lt;/host&gt;
 *   &lt;host&gt;192.168.15.3/255.255.255.0&lt;/host&gt;
 * &lt;/remote&gt;
 * </pre>
 *
 * @author roland
 * @since 02.09.11
 *
 */
public class NetworkChecker extends AbstractChecker<String[]> {

    private final Set<String> allowedHostsSet;
    private final Set<Inet6Address> allowedIP6HostsSet;
    private Set<String> allowedSubnetsSet;

    // Simple patterns, could be more specific
    private static final Pattern IP_PATTERN = Pattern.compile("^[\\d.]+$");
    private static final Pattern IP6_PATTERN = Pattern.compile("^[\\da-fA-F:]+$");
    private static final Pattern SUBNET_IP4_PATTERN = Pattern.compile("^[\\d.]+/[\\d.]+$");
    private static final Pattern SUBNET_IP6_PATTERN = Pattern.compile("^[\\da-fA-F:]+/[\\d.]+$");

    /**
     * Construct this checker from a given document
     *
     * @param pDoc document to examine for &lt;remote&gt; tags.
     */
    public NetworkChecker(Document pDoc) {
        NodeList nodes = pDoc.getElementsByTagName("remote");
        if (nodes.getLength() == 0) {
            // No restrictions found - no <remote> at all means "allow all"
            // Empty <remote> (no <host> children) would mean "allow none"
            allowedHostsSet = null;
            allowedIP6HostsSet = null;
            return;
        }

        allowedHostsSet = new HashSet<>();
        allowedIP6HostsSet = new HashSet<>();
        for (int i = 0;i<nodes.getLength();i++) {
            Node node = nodes.item(i);
            NodeList childs = node.getChildNodes();
            for (int j = 0;j<childs.getLength();j++) {
                Node hostNode = childs.item(j);
                if (hostNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                assertNodeName(hostNode,"host");
                String host = hostNode.getTextContent().trim().toLowerCase();
                if (host.startsWith("[") && host.endsWith("]")) {
                    // trim IPv6 bracket notation
                    host = host.substring(1, host.length() - 1);
                }
                if (SUBNET_IP4_PATTERN.matcher(host).matches() || SUBNET_IP6_PATTERN.matcher(host).matches()) {
                    if (allowedSubnetsSet == null) {
                        allowedSubnetsSet = new HashSet<>();
                    }
                    allowedSubnetsSet.add(host);
                } else {
                    if (host.contains(":")) {
                        // assume it may be IPv6 address, so we have to be able to match ffff::1 and ffff:0:0:0:0:0:0:1
                        try {
                            allowedIP6HostsSet.add((Inet6Address) Inet6Address.getByName(host));
                        } catch (UnknownHostException e) {
                            throw new IllegalArgumentException("Invalid IPv6 address \"" + host + "\"", e);
                        }
                    } else {
                        allowedHostsSet.add(host);
                    }
                }
            }
        }
    }

    /**
     * Check for one or more hosts.
     *
     * @param pHostOrAddresses array of host names or IP addresses
     * @return true if all the given names pass this checker.
     */
    @Override
    public boolean check(String[] pHostOrAddresses) {
        if (allowedHostsSet == null) {
            // No <remote> section in policy - allow all
            return true;
        }
        // Null or empty array - no information about source (client) address/name, don't trust
        if (pHostOrAddresses == null || pHostOrAddresses.length == 0) {
            return false;
        }
        // each incoming address (original client and proxies) must be trusted
        for (String addr : pHostOrAddresses) {
            if (!isAllowed(addr)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Verify single IP address/host name
     * @param addr
     * @return
     */
    private boolean isAllowed(String addr) {
        // 1. Plain hostname / IPv4 literal match
        if (allowedHostsSet.contains(addr)) {
            return true;
        }
        // 2. IPv6 address match (normalises e.g. ffff::1 == ffff:0:0:0:0:0:0:1)
        //    Strip any scope ID suffix first (e.g. "fe80::1%2" or "fe80::1%eth0") so that
        //    Inet6Address.equals() — which includes scope in its comparison — matches policy
        //    entries that were stored without a scope.
        try {
            InetAddress resolved = Inet6Address.getByName(addr);
            if (resolved instanceof Inet6Address ip6) {
                if (ip6.getScopedInterface() != null || ip6.getScopeId() > 0) {
                    int percent = addr.indexOf('%');
                    if (percent != -1) {
                        addr = addr.substring(0, percent);
                        // resolve again before we move to subnet matching
                        resolved = Inet6Address.getByName(addr);
                    }
                }
                if (resolved instanceof Inet6Address ip6resolved && allowedIP6HostsSet.contains(ip6resolved)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            return false;
        }
        // 3. Subnet match (IPv4 or IPv6 literals only)
        if (allowedSubnetsSet != null &&
                (IP_PATTERN.matcher(addr).matches() || IP6_PATTERN.matcher(addr).matches())) {
            for (String subnet : allowedSubnetsSet) {
                if (IpChecker.matches(subnet, addr)) {
                    return true;
                }
            }
        }
        return false;
    }
}
