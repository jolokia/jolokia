package org.jolokia.restrictor.policy;

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

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;


import org.jolokia.util.IpChecker;
import org.w3c.dom.*;

/**
 * Check whether a host is allowed to access the agent. The restriction
 * can be specified in the policy file with the <code>&lt;remote&gt;</code>
 * tag. Either plain host or subnet (in the CIDR notation) can be specified
 * <br/>
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

    private Set<String> allowedHostsSet;
    private Set<String> allowedSubnetsSet;

    // Simple patterns, could be mor specific
    private static final Pattern IP_PATTERN = Pattern.compile("^[\\d.]+$");
    private static final Pattern SUBNET_PATTERN = Pattern.compile("^[\\d.]+/[\\d.]+$");

    /**
     * Construct this checker from a given document
     *
     * @param pDoc document to examine for &lt;remote&gt; tags.
     */
    public NetworkChecker(Document pDoc) {
        NodeList nodes = pDoc.getElementsByTagName("remote");
        if (nodes.getLength() == 0) {
            // No restrictions found
            allowedHostsSet = null;
            return;
        }

        allowedHostsSet = new HashSet<String>();
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
                if (SUBNET_PATTERN.matcher(host).matches()) {
                    if (allowedSubnetsSet == null) {
                        allowedSubnetsSet = new HashSet<String>();
                    }
                    allowedSubnetsSet.add(host);
                } else {
                    allowedHostsSet.add(host);
                }
            }
        }
    }

    /**
     * Check for one or more hosts.
     *
     * @param pHostOrAddresses array of host names or IP addresses
     * @return true if one of the given name passes this checker.
     */
    @Override
    public boolean check(String[] pHostOrAddresses) {
        if (allowedHostsSet == null) {
            return true;
        }
        for (String addr : pHostOrAddresses) {
            if (allowedHostsSet.contains(addr)) {
                return true;
            }
            if (allowedSubnetsSet != null && IP_PATTERN.matcher(addr).matches()) {
                for (String subnet : allowedSubnetsSet) {
                    if (IpChecker.matches(subnet, addr)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
