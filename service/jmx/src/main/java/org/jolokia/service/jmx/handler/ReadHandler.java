package org.jolokia.service.jmx.handler;

import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.jolokia.server.core.request.JolokiaReadRequest;
import org.jolokia.server.core.service.serializer.ValueFaultHandler;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;

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


/**
 * Handler for managing READ requests for reading attributes.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class ReadHandler extends AbstractCommandHandler<JolokiaReadRequest> {

    // MBean Handler used for extracting MBean Meta data
    private static final MBeanServerAccess.MBeanAction<MBeanInfo> MBEAN_INFO_HANDLER =
            new MBeanServerAccess.MBeanAction<>() {
                /** {@inheritDoc} */
                public MBeanInfo execute(MBeanServerConnection pConn, ObjectName pName, Object... extraArgs)
                        throws ReflectionException, InstanceNotFoundException, IOException {
                    try {
                        return pConn.getMBeanInfo(pName);
                    } catch (IntrospectionException e) {
                        throw new IllegalArgumentException("Cannot inspect " + pName + ": " + e, e);
                    }
                }
            };

    // MBean Handler for getting an attribute
    private static final MBeanServerAccess.MBeanAction<Object> MBEAN_ATTRIBUTE_READ_HANDLER =
            new MBeanServerAccess.MBeanAction<>() {
                /** {@inheritDoc} */
                public Object execute(MBeanServerConnection pConn, ObjectName pName, Object... extraArgs)
                        throws ReflectionException, InstanceNotFoundException, IOException, MBeanException, AttributeNotFoundException {
                    String attribute = (String) extraArgs[0];
                    return pConn.getAttribute(pName, attribute);
                }
            };


    /** {@inheritDoc} */
    public RequestType getType() {
        return RequestType.READ;
    }

    /**
     * For a simple requests (one MBean, one attribute) we let the dispatching of the servers
     * done by the upper level. If the request is for an MBean pattern or multiple attributes
     * are required, we try multiple requests for multiple server.
     *
     * @param pRequest request to decide on whether to handle all request at once
     * @return true if this is a multi attribute request, has an MBean pattern to look for or is a request for
     *         all attributes.
     */
    @Override
    public boolean handleAllServersAtOnce(JolokiaReadRequest pRequest) {
        return pRequest.getObjectName().isPattern() || pRequest.isMultiAttributeMode() || !pRequest.hasAttribute();
    }

    /**
     * Used for a request to a single attribute from a single MBean. Merging of MBeanServers is done
     * one layer above.
     *
     *
     * @param pServer server on which to request the attribute
     * @param pRequest the request itself.
     * @return the attribute's value
     */
    @Override
    public Object doHandleSingleServerRequest(MBeanServerConnection pServer, JolokiaReadRequest pRequest)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        checkRestriction(pRequest.getObjectName(), pRequest.getAttributeName());
        return pServer.getAttribute(pRequest.getObjectName(), pRequest.getAttributeName());
    }

    /** {@inheritDoc} */
    @Override
    public Object doHandleAllServerRequest(MBeanServerAccess pServerManager, JolokiaReadRequest pRequest, Object pPreviousResult)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        // Read command is still exclusive yet (no merging of bulk read requests). If a non-exclusive usage
        // is going to be implemented, then pPreviousResult must taken care of that it might hold bulk read results
        // from previous calls.
        ObjectName oName = pRequest.getObjectName();
        ValueFaultHandler faultHandler = pRequest.getValueFaultHandler();
        if (oName.isPattern()) {
            return fetchAttributesForMBeanPattern(pServerManager, pRequest);
        } else {
            return fetchAttributes(pServerManager,oName,pRequest.getAttributeNames(),faultHandler);
        }
    }

    private Object fetchAttributesForMBeanPattern(MBeanServerAccess pServerManager, JolokiaReadRequest pRequest)
            throws IOException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException {
        ObjectName objectName = pRequest.getObjectName();
        ValueFaultHandler faultHandler = pRequest.getValueFaultHandler();
        Set<ObjectName> names = searchMBeans(pServerManager, objectName);
        Map<String,Object> ret = new HashMap<>();
        List<String> attributeNames = pRequest.getAttributeNames();
        for (ObjectName name : names) {
            try {
                if (!pRequest.hasAttribute()) {
                    Map<?, ?> values = (Map<?, ?>) fetchAttributes(pServerManager,name, null, faultHandler);
                    if (!values.isEmpty()) {
                        ret.put(pRequest.getOrderedObjectName(name),values);
                    }
                } else {
                    List<String> filteredAttributeNames = filterAttributeNames(pServerManager, name, attributeNames);
                    if (filteredAttributeNames.isEmpty()) {
                        continue;
                    }
                    ret.put(pRequest.getOrderedObjectName(name),
                            fetchAttributes(pServerManager,name,filteredAttributeNames, faultHandler));
                }
            } catch (InstanceNotFoundException exp) {
                // Since MBean can be registered/deregistered dynamically, it can happen here, that
                // an MBean has been already unregistered in the meantime. We simply ignore an InstanceNotFoundException
                // here and go on ....
            }
        }
        if (ret.isEmpty()) {
            throw new IllegalArgumentException("No matching attributes " +
                    pRequest.getAttributeNames() + " found on MBeans " + names);
        }
        return ret;
    }

    private Set<ObjectName> searchMBeans(MBeanServerAccess pServerManager, ObjectName pObjectName) throws IOException, InstanceNotFoundException {
        Set<ObjectName> names = pServerManager.queryNames(pObjectName);
        if (names.isEmpty()) {
            throw new InstanceNotFoundException("No MBean with pattern " + pObjectName +
                    " found for reading attributes");
        }
        return names;
    }

    // Return only those attributes of an mbean which has one of the given names
    private List<String> filterAttributeNames(MBeanServerAccess pSeverManager,ObjectName pName, List<String> pNames)
            throws IOException, ReflectionException, MBeanException, AttributeNotFoundException, InstanceNotFoundException {
        Set<String> attrs = new HashSet<>(getAllAttributesNames(pSeverManager, pName));
        List<String> ret = new ArrayList<>();
        for (String name : pNames) {
            if (attrs.contains(name)) {
                ret.add(name);
            }
        }
        return ret;
    }

    @SuppressWarnings("TryWithIdenticalCatches")
    private Object fetchAttributes(MBeanServerAccess pServerManager, ObjectName pMBeanName, List<String> pAttributeNames,
                                   ValueFaultHandler pFaultHandler)
            throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {

        List<String> attributes = resolveAttributes(pServerManager, pMBeanName, pAttributeNames);
        Map<String,Object> ret = new HashMap<>();

        for (String attribute : attributes) {
            try {
                checkRestriction(pMBeanName, attribute);
                ret.put(attribute,getAttribute(pServerManager, pMBeanName, attribute));
            } catch (MBeanException e) {
                // The fault handler might to decide to rethrow the
                // exception in which case nothing is put extra into ret.
                // Otherwise, the replacement value as returned by the
                // fault handler is inserted.
                ret.put(attribute, pFaultHandler.handleException(e));
            } catch (IllegalArgumentException e) {
                ret.put(attribute, pFaultHandler.handleException(e));
            } catch (ReflectionException e) {
                ret.put(attribute, pFaultHandler.handleException(e));
            } catch (IOException e) {
                ret.put(attribute, pFaultHandler.handleException(e));
            } catch (RuntimeException e) {
                if (e.getCause() instanceof UnsupportedOperationException) {
                    ret.put(attribute, "Unsupported");
                } else {
                    ret.put(attribute, pFaultHandler.handleException(e));
                }
            } catch (AttributeNotFoundException e) {
                ret.put(attribute, pFaultHandler.handleException(e));
            }
        }
        return ret;
    }

    // Resolve attributes and look up attribute names if all attributes need to be fetched.
    private List<String> resolveAttributes(MBeanServerAccess pServers, ObjectName pMBeanName, List<String> pAttributeNames)
            throws IOException, ReflectionException, MBeanException, AttributeNotFoundException, InstanceNotFoundException {
        List<String> attributes = pAttributeNames;
        if (shouldAllAttributesBeFetched(pAttributeNames)) {
            // All attributes are requested, we look them up now
            attributes = getAllAttributesNames(pServers,pMBeanName);
        }
        return attributes;
    }

    private boolean shouldAllAttributesBeFetched(List<String> pAttributeNames) {
        return pAttributeNames == null || pAttributeNames.isEmpty() || pAttributeNames.size() == 1 && pAttributeNames.get(0) == null;
    }

    // Get the MBeanInfo from one of the provided MBeanServers
    private MBeanInfo getMBeanInfo(MBeanServerAccess pServerManager, ObjectName pObjectName)
            throws IOException, ReflectionException, MBeanException, AttributeNotFoundException, InstanceNotFoundException {
        return pServerManager.call(pObjectName, MBEAN_INFO_HANDLER);
    }

    // Try multiple servers for fetching an attribute
    private Object getAttribute(MBeanServerAccess pServerManager, ObjectName pMBeanName, String attribute)
            throws MBeanException, ReflectionException, IOException, AttributeNotFoundException, InstanceNotFoundException {
        return pServerManager.call(pMBeanName, MBEAN_ATTRIBUTE_READ_HANDLER, attribute);
    }

    // Return a set of attributes as a map with the attribute name as key and their values as values
    private List<String> getAllAttributesNames(MBeanServerAccess pServerManager, ObjectName pObjectName)
            throws IOException, ReflectionException, MBeanException, AttributeNotFoundException, InstanceNotFoundException {
        MBeanInfo mBeanInfo = getMBeanInfo(pServerManager, pObjectName);
        List<String> ret = new ArrayList<>();
        for (MBeanAttributeInfo attrInfo : mBeanInfo.getAttributes()) {
            if (attrInfo.isReadable()) {
                ret.add(attrInfo.getName());
            }
        }
        return ret;
    }

    private void checkRestriction(ObjectName mBeanName, String attribute) {
        if (!context.isAttributeReadAllowed(mBeanName,attribute)) {
            throw new SecurityException("Reading attribute " + attribute +
                    " is forbidden for MBean " + mBeanName.getCanonicalName());
        }
    }

    /**
     * We override it here with a noop since we do a more fine grained
     * check during processing of the request.
     */
    @Override
    protected void checkForRestriction(JolokiaReadRequest pRequest) {

    }
}
