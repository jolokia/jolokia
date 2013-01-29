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
