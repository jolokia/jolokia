package org.jolokia.detector;

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

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.management.*;

import org.jolokia.backend.executor.MBeanServerExecutor;

/**
 * Base class for server detectors
 * 
 * @author roland
 * @since 05.11.10
 */
public abstract class AbstractServerDetector implements ServerDetector {

    /**
     * Check for the existence of a certain MBean. All known MBeanServers are queried
     *
     * @param pMBeanServerExecutor mbean servers to query for
     * @param pMbeanPattern MBean name pattern for MBeans to check for
     * @return set of {@link ObjectName}s if the pattern matches, or an empty set if not mbean has been found
     */
    protected Set<ObjectName> searchMBeans(MBeanServerExecutor pMBeanServerExecutor, String pMbeanPattern) {
        try {
            ObjectName oName = new ObjectName(pMbeanPattern);
            return pMBeanServerExecutor.queryNames(oName);
        } catch (MalformedObjectNameException e) {
            return new HashSet<ObjectName>();
        } catch (IOException e) {
            return new HashSet<ObjectName>();
        }
    }

    /**
     * Check whether a certain MBean exists
     *
     * @param pMBeanServerManger set of MBeanServers to query for
     * @param pObjectName the objectname to check. Can be a pattern in which case this method
     *        return true if one or more MBeans of all MBeanServers match this pattern
     * @return true if at least one MBean of the given name (or pattern) exists
     */
    protected boolean mBeanExists(MBeanServerExecutor pMBeanServerManger,String pObjectName) {
        return searchMBeans(pMBeanServerManger,pObjectName).size() > 0;
    }

    /**
     * Get the string representation of an attribute
     *
     * @param pMBeanServerExecutor set of MBeanServers to query. The first one wins.
     * @param pMBean object name of MBean to lookup
     * @param pAttribute attribute to lookup
     * @return string value of attribute or <code>null</code> if the attribute could not be fetched
     */
    protected String getAttributeValue(MBeanServerExecutor pMBeanServerExecutor,String pMBean,String pAttribute) {
        try {
            ObjectName oName = new ObjectName(pMBean);
            return getAttributeValue(pMBeanServerExecutor,oName,pAttribute);
        } catch (MalformedObjectNameException e) {
            return null;
        }
    }

    /**
     * Get the string representation of an attribute
     *
     *
     *
     * @param pMBeanServerExecutor set of MBeanServers to query. The first one wins.
     * @param pMBean name of MBean to lookup
     * @param pAttribute attribute to lookup
     * @return string value of attribute or <code>null</code> if the attribute could not be fetched
     */
    protected String getAttributeValue(MBeanServerExecutor pMBeanServerExecutor, final ObjectName pMBean, final String pAttribute) {
        try {
            return pMBeanServerExecutor.call(pMBean, GET_ATTRIBUTE_HANDLER, pAttribute);
        } catch (IOException e) {
            return null;
        } catch (ReflectionException e) {
            return null;
        } catch (JMException e) {
            return null;
        }
    }

    // Handler for fetching an attribute
    private static final MBeanServerExecutor.MBeanAction<String> GET_ATTRIBUTE_HANDLER = new MBeanServerExecutor.MBeanAction<String>() {
        /** {@inheritDoc} */
        public String execute(MBeanServerConnection pConn, ObjectName pName, Object... extraArgs) throws ReflectionException, InstanceNotFoundException, IOException, MBeanException, AttributeNotFoundException {
            Object attr = pConn.getAttribute(pName, (String) extraArgs[0]);
            return attr != null ? attr.toString() : null;
        }
    };

    /**
     * Get a single attribute for a given MBeanName pattern.
     *
     * @param pMBeanServerExecutor MBeanServer manager to query
     * @param pMBeanName a MBean name or pattern. If multiple MBeans are found, each is queried for the attribute
     * @param pAttribute the attribute to lookup
     * @return the string value of the attribute or null if either no MBeans could be found, or 0 or more than 1 attribute
     *         are found on those mbeans
     */
    protected String getSingleStringAttribute(MBeanServerExecutor pMBeanServerExecutor, String pMBeanName, String pAttribute) {
        Set<ObjectName> serverMBeanNames = searchMBeans(pMBeanServerExecutor, pMBeanName);
        if (serverMBeanNames.size() == 0) {
            return null;
        }
        Set<String> attributeValues = new HashSet<String>();
        for (ObjectName oName : serverMBeanNames) {
            String val = getAttributeValue(pMBeanServerExecutor, oName, pAttribute);
            if (val != null) {
                attributeValues.add(val);
            }
        }
        if (attributeValues.size() == 0 || attributeValues.size() > 1) {
            return null;
        }
        return attributeValues.iterator().next();
    }

    /**
     * Get the version number from a JSR-77 compliant server
     *
     *
     * @param pMbeanServers servers to query
     * @return version number or null if not found.
     */
    protected String getVersionFromJsr77(MBeanServerExecutor pMbeanServers) {
        Set<ObjectName> names = searchMBeans(pMbeanServers, "*:j2eeType=J2EEServer,*");
        // Take the first one
        if (names.size() > 0) {
            return getAttributeValue(pMbeanServers, names.iterator().next(), "serverVersion");
        }
        return null;
    }

    /**
     * Do nothing by default, leaving the implementation
     * optional for each specific detector
     *
     * @param pServers servers to add
     */
    public void addMBeanServers(Set<MBeanServerConnection> pServers) {
    }
}
