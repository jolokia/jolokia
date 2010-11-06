/*
 * Copyright 2009-2010 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.detector;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.Set;

/**
 * Base class for server detectors
 * 
 * @author roland
 * @since 05.11.10
 */
abstract public class AbstractServerDetector implements ServerDetector {

    /**
     * Check for the existence of a given class
     *
     * @param pClassName class name to check
     * @return true if the class could be loaded by the thread's conext class loader, false otherwise
     */
    protected boolean checkForClass(String pClassName) {
        return getClass(pClassName) != null;
    }

    /**
     * Load a certain class from the thread context class loader
     *
     * @param pClassName class name to load
     * @return the class
     * @throws ClassNotFoundException if the class could not be found
     */
    protected Class getClass(String pClassName) {
        try {
            ClassLoader loader = getClassLoader();
            return Class.forName(pClassName,false, loader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Check for the existence of a certain MBean. All known MBeanServers are queried
     *
     * @param pMbeanServers mbean servers to query for
     * @param pMbeanPattern MBean name pattern for MBeans to check for
     * @return set of {@link ObjectName}s if the pattern matches, null if no match was found
     */
    protected Set<ObjectName> searchMBeans(Set<MBeanServer> pMbeanServers, String pMbeanPattern) {
        try {
            ObjectName oName = new ObjectName(pMbeanPattern);
            for (MBeanServer s : pMbeanServers) {
                Set<ObjectName> names = s.queryNames(oName,null);
                if (names != null && names.size() > 0) {
                    return names;
                }
            }
            return null;
        } catch (MalformedObjectNameException e) {
            return null;
        }
    }

    /**
     * Get the string representation of an attribute
     *
     * @param pMbeanServers set of MBeanServers to query. The first one wins.
     * @param pMBean object name of MBean to lookup
     * @param pAttribute attribute to lookup
     * @return string value of attribute or <code>null</code> if the attribute could not be fetched
     */
    protected Object getAttributeValue(Set<MBeanServer> pMbeanServers,String pMBean,String pAttribute) {
        try {
            ObjectName oName = new ObjectName(pMBean);
            return getAttributeValue(pMbeanServers,oName,pAttribute);
        } catch (JMException e) {
            return null;
        }
    }

    protected Object getAttributeValue(Set<MBeanServer> pMbeanServers,ObjectName pMBean,String pAttribute) {
        try {
            for (MBeanServer s : pMbeanServers) {
                Object attr = s.getAttribute(pMBean,pAttribute);
                if (attr != null) {
                    return attr;
                }
            }
            return null;
        } catch (JMException e) {
            return null;
        }
    }


    /**
     * Get the version number from a JSR-77 compliant server
     *
     * @param pMbeanServers servers to query
     * @return version number or null if not found.
     */
    protected String getVersionFromJsr77(Set<MBeanServer> pMbeanServers) {
        Set<ObjectName> names = searchMBeans(pMbeanServers,"*:j2eeType=J2EEServer,*");
        // Take the first one
        if (names != null && names.size() > 0) {
            return (String) getAttributeValue(pMbeanServers,names.iterator().next(),"serverVersion");
        }
        return null;
    }


    // ===========================================================================

    protected ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

}
