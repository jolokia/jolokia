package org.jolokia.handler;

import org.jolokia.JmxRequest;
import org.jolokia.config.Restrictor;

import javax.management.*;
import java.io.IOException;
import java.util.*;

/*
 * jmx4perl - WAR Agent for exporting JMX via JSON
 *
 * Copyright (C) 2009 Roland Huß, roland@cpan.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * A commercial license is available as well. Please contact roland@cpan.org for
 * further details.
 */

/**
 * Handler for managing READ requests for reading attributes.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class ReadHandler extends JsonRequestHandler {

    public ReadHandler(Restrictor pRestrictor) {
        super(pRestrictor);
    }

    @Override
    public JmxRequest.Type getType() {
        return JmxRequest.Type.READ;
    }

    @Override
    public Object doHandleRequest(MBeanServerConnection pServer, JmxRequest pRequest)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        ObjectName oName = pRequest.getObjectName();
        JmxRequest.ValueFaultHandler faultHandler = pRequest.getValueFaultHandler();
        if (oName.isPattern()) {
            return fetchAttributesForMBeanPattern(pServer, pRequest);
        } else {
            return fetchAttributes(pServer,oName,pRequest.getAttributeNames(),faultHandler,!pRequest.isSingleAttribute());
        }
    }

    private Object fetchAttributesForMBeanPattern(MBeanServerConnection pServer, JmxRequest pRequest)
            throws IOException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException {
        ObjectName objectName = pRequest.getObjectName();
        JmxRequest.ValueFaultHandler faultHandler = pRequest.getValueFaultHandler();
        Set<ObjectName> names = searchMBeans(pServer, objectName);
        Map<String,Object> ret = new HashMap<String, Object>();
        List<String> attributeNames = pRequest.getAttributeNames();
        boolean fetchAll =  attributeNames == null || (attributeNames.contains(null));
        for (ObjectName name : names) {
            if (fetchAll) {
                Map values = (Map) fetchAttributes(pServer,name, null, faultHandler,true /* always as map */);
                if (values != null && values.size() > 0) {
                    ret.put(name.getCanonicalName(),values);
                }
            } else {
                List<String> filteredAttributeNames = filterAttributeNames(pServer,name,attributeNames);
                if (filteredAttributeNames.size() == 0) {
                    continue;
                }
                ret.put(name.getCanonicalName(),
                        fetchAttributes(pServer,name,filteredAttributeNames, faultHandler,true /* always as map */));
            }
        }
        if (ret.size() == 0) {
            throw new IllegalArgumentException("No matching attributes " +
                    pRequest.getAttributeNames() + " found on MBeans " + names);
        }
        return ret;
    }

    private Set<ObjectName> searchMBeans(MBeanServerConnection pServer, ObjectName pObjectName) throws IOException, InstanceNotFoundException {
        Set<ObjectName> names = pServer.queryNames(pObjectName,null);
        if (names == null || names.size() == 0) {
            throw new InstanceNotFoundException("No MBean with pattern " + pObjectName +
                    " found for reading attributes");
        }
        return names;
    }

    // Return only those attributes of an mbean which has one of the given names
    private List<String> filterAttributeNames(MBeanServerConnection pServer,ObjectName pName, List<String> pNames)
            throws InstanceNotFoundException, IOException, ReflectionException {
        Set<String> attrs = new HashSet<String>(getAllAttributesNames(pServer,pName));
        List<String> ret = new ArrayList<String>();
        for (String name : pNames) {
            if (attrs.contains(name)) {
                ret.add(name);
            }
        }
        return ret;
    }

    private Object fetchAttributes(MBeanServerConnection pServer, ObjectName pMBeanName, List<String> pAttributeNames,
                                   JmxRequest.ValueFaultHandler pFaultHandler,boolean pAlwaysAsMap)
            throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        if (pAttributeNames != null && pAttributeNames.size() > 0 &&
                !(pAttributeNames.size() == 1 && pAttributeNames.get(0) == null)) {
            if (pAttributeNames.size() == 1) {
                checkRestriction(pMBeanName, pAttributeNames.get(0));
                // When only a single attribute is requested, return it as plain value (backward compatibility)
                Object ret = pServer.getAttribute(pMBeanName, pAttributeNames.get(0));
                if (pAlwaysAsMap) {
                    Map<String,Object> retMap = new HashMap<String, Object>();
                    retMap.put(pAttributeNames.get(0),ret);
                    return retMap;
                } else {
                    return ret;
                }
            } else {
                return fetchMultiAttributes(pServer,pMBeanName,pAttributeNames,pFaultHandler);
            }
        } else {
            // Return the value of all attributes stored
            List<String> allAttributesNames = getAllAttributesNames(pServer,pMBeanName);
            return fetchMultiAttributes(pServer,pMBeanName,allAttributesNames,pFaultHandler);
        }
    }

    // Return a set of attributes as a map with the attribute name as key and their values as values
    private Map<String,Object> fetchMultiAttributes(MBeanServerConnection pServer, ObjectName pMBeanName, List<String> pAttributeNames,
                                                    JmxRequest.ValueFaultHandler pFaultHandler)
    throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        Map<String,Object> ret = new HashMap<String, Object>();
        for (String attribute : pAttributeNames) {
            checkRestriction(pMBeanName, attribute);
            try {
                ret.put(attribute,pServer.getAttribute(pMBeanName, attribute));
            } catch (MBeanException e) {
                // The fault handler might to decide to rethrow the
                // exception in which case nothing is put extra intor ret.
                // Otherwise, the replacement value as returned by the
                // fault handler is inserted.
                ret.put(attribute, pFaultHandler.handleException(e));
            } catch (AttributeNotFoundException e) {
                ret.put(attribute, pFaultHandler.handleException(e));
            } catch (InstanceNotFoundException e) {
                ret.put(attribute, pFaultHandler.handleException(e));
            } catch (ReflectionException e) {
                ret.put(attribute, pFaultHandler.handleException(e));
            } catch (IOException e) {
                ret.put(attribute, pFaultHandler.handleException(e));
            } catch (RuntimeException e) {
                ret.put(attribute, pFaultHandler.handleException(e));
            }

        }
        return ret;
    }

    private List<String> getAllAttributesNames(MBeanServerConnection pServer, ObjectName pObjectName)
            throws InstanceNotFoundException, IOException, ReflectionException {
        try {
            MBeanInfo mBeanInfo;
            mBeanInfo = pServer.getMBeanInfo(pObjectName);
            List<String> ret = new ArrayList<String>();
            for (MBeanAttributeInfo attrInfo : mBeanInfo.getAttributes()) {
                ret.add(attrInfo.getName());
            }
            return ret;
        } catch (IntrospectionException e) {
            throw new IllegalStateException("Internal error while retrieving list: " + e,e);
        }
    }

    private void checkRestriction(ObjectName mBeanName, String attribute) {
        if (!getRestrictor().isAttributeReadAllowed(mBeanName,attribute)) {
            throw new SecurityException("Reading attribute " + attribute +
                    " is forbidden for MBean " + mBeanName.getCanonicalName());
        }
    }

    @Override
    // We override it here with a noop since we do a more fine grained
    // check during processin of the request.
    protected void checkForType(JmxRequest pRequest) {

    }
}
