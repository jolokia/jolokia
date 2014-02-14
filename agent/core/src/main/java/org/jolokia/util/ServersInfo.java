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

package org.jolokia.util;

import java.lang.management.ManagementFactory;
import java.util.Set;

import javax.management.*;

/**
 * Simple helper class for dumping out information about MBeanServers
* @author roland
* @since 22.01.13
*/
public final class ServersInfo {

    // Only utility methods
    private ServersInfo() {}

    /**
     * Dump out a list of MBeanServer with some statistics.
     *
     * @param servers server to dump
     * @return statistics as strings
     */
    public static String dump(Set<MBeanServerConnection> servers) {
            StringBuffer ret = new StringBuffer();
            ret.append("Found ").append(servers.size()).append(" MBeanServers\n");
            for (MBeanServerConnection c : servers) {
                MBeanServer s = (MBeanServer) c;
                ret.append("    ")
                   .append("++ ")
                   .append(s.toString())
           .append(": default domain = ")
           .append(s.getDefaultDomain())
           .append(", ")
           .append(s.getMBeanCount())
           .append(" MBeans\n");

            ret.append("        Domains:\n");
            for (String d : s.getDomains()) {
                appendDomainInfo(ret, s, d);
            }
        }
        ret.append("\n");
        ret.append("Platform MBeanServer: ")
           .append(ManagementFactory.getPlatformMBeanServer())
           .append("\n");
        return ret.toString();
    }


    private static void appendDomainInfo(StringBuffer pRet, MBeanServer pServer, String pDomain) {
        try {
            pRet.append("         == ").append(pDomain).append("\n");
            Set<ObjectInstance> beans = pServer.queryMBeans(new ObjectName(pDomain + ":*"),null);
            for (ObjectInstance o : beans) {
                String n = o.getObjectName().getCanonicalKeyPropertyListString();
                pRet.append("              ").append(n).append("\n");
            }
        } catch (MalformedObjectNameException e) {
            // Shouldnt happen
            pRet.append("              INTERNAL ERROR: ").append(e).append("\n");
        }
    }
}
