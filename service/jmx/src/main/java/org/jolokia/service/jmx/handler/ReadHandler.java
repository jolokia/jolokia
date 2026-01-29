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
package org.jolokia.service.jmx.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.jolokia.core.service.serializer.ValueFaultHandler;
import org.jolokia.server.core.request.JolokiaReadRequest;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;

/**
 * Handler for managing READ requests for reading attributes.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class ReadHandler extends AbstractCommandHandler<JolokiaReadRequest> {

    @Override
    public RequestType getType() {
        return RequestType.READ;
    }

    @Override
    protected void checkForRestriction(JolokiaReadRequest pRequest) {
        // We override it here to do nothing, since we do a more fine grained check during processing of the request.
    }

    /**
     * For simple requests (one MBean, no pattern, one attribute) we use the
     * {@link MBeanServerAccess#call(ObjectName, MBeanServerAccess.MBeanAction, Object...)} to quickly return
     * first available result. If the request is for an MBean pattern or multiple (or all) attributes
     * are required, we combine multiple requests for multiple servers.
     *
     * @param pRequest request to decide on whether to handle all request at once
     * @return true if this is a multi attribute request, has an MBean pattern to look for or is a request for
     *         all attributes.
     */
    @Override
    public boolean handleAllServersAtOnce(JolokiaReadRequest pRequest) {
        return pRequest.getObjectName().isPattern() || pRequest.isMultiAttributeMode();
    }

    /**
     * Used for a request for a single attribute from a single MBean.
     *
     * @param pServer server on which to request the attribute
     * @param pRequest the request itself.
     * @return the attribute's value
     */
    @Override
    public Object doHandleSingleServerRequest(MBeanServerConnection pServer, JolokiaReadRequest pRequest)
          throws IOException, JMException {
        // one simple restriction check here, because parent checkForRestriction() was a no-op
        checkRestriction(pRequest.getObjectName(), pRequest.getAttributeName());

        // propagate all the exceptions. request should include an attribute name at this stage
        return pServer.getAttribute(pRequest.getObjectName(), pRequest.getAttributeName());
    }

    @Override
    public Object doHandleAllServerRequest(MBeanServerAccess jmxAccess, JolokiaReadRequest pRequest, Object pPreviousResult)
            throws IOException, JMException {
        // Read command is now exclusive, so we ignore previous result.
        // If a non-exclusive usage is going to be implemented in the future, then pPreviousResult must taken care of
        // that it might hold bulk read results from previous calls.

        ObjectName oName = pRequest.getObjectName();
        ValueFaultHandler faultHandler = pRequest.getValueFaultHandler();

        // this ReadRequest is either for MBean pattern or for multiple (all) attributes

        if (oName.isPattern()) {
            // the keys of the returned map will be canonical MBean names
            return fetchAttributesForMBeanPattern(jmxAccess, oName, pRequest, faultHandler);
        } else {
            // the keys of the returned map will be attribute names. A request for a collection of attributes
            // (even one) is not a strict one, because MBeanServer.getAttributes() may return less attributes than asked
            return fetchAttributesForMBean(jmxAccess, oName, pRequest, faultHandler, pRequest.isMultiAttributeMode() ? ResolveMode.ALL_REQUESTED : ResolveMode.STRICT);
        }
    }

    /**
     * Invoke {@link MBeanServerConnection#getAttribute}/{@link MBeanServerConnection#getAttributes} on all
     * {@link ObjectName object names} matching a pattern - a query is performed first. The returned map uses
     * extra top-level to indicate the actual MBeans.
     *
     * @param jmxAccess
     * @param pName
     * @param pRequest
     * @param faultHandler
     * @return
     * @throws IOException
     * @throws JMException
     */
    private Map<String, Object> fetchAttributesForMBeanPattern(MBeanServerAccess jmxAccess, ObjectName pName, JolokiaReadRequest pRequest, ValueFaultHandler faultHandler)
            throws IOException, JMException {
        // pattern is used to search for actual names
        Set<ObjectName> names = searchMBeans(jmxAccess, pName);

        Map<String, Object> result = new LinkedHashMap<>();

        for (ObjectName name : names) {
            try {
                String key = pRequest.getOrderedObjectName(name);
                Map<String, Object> values = fetchAttributesForMBean(jmxAccess, name, pRequest, faultHandler, ResolveMode.FILTERED);
                if (!values.isEmpty()) {
                    result.put(key, values);
                }
            } catch (InstanceNotFoundException ignored) {
                // Since MBean can be registered/deregistered dynamically, it can happen here, that
                // an MBean has been already unregistered in the meantime. We simply ignore an InstanceNotFoundException
                // here and go on ....
            }
        }
        if (result.isEmpty()) {
            // read handler assumes there's something to return
            throw new AttributeNotFoundException("No attribute found for any MBean matching " + pName + " pattern");
        }
        return result;
    }

    /**
     * Main optimized logic for fetch one, more or all attributes for a single {@link ObjectName}.
     *
     * @param jmxAccess
     * @param pName
     * @param pRequest
     * @param pFaultHandler
     * @param pMode whether we should throw an exception if asking for non-existing attributes.
     * @return
     * @throws IOException
     * @throws JMException
     */
    private Map<String, Object> fetchAttributesForMBean(MBeanServerAccess jmxAccess, ObjectName pName, JolokiaReadRequest pRequest, ValueFaultHandler pFaultHandler, ResolveMode pMode)
            throws IOException, JMException {

        Map<String, Object> result = new LinkedHashMap<>();

        // "resolve" attributes, so we can work with actually available attributes. We still can fail
        // fetching their values
        List<String> attributes = resolveAttributes(jmxAccess, pName, pRequest, pMode, pFaultHandler);

        // fetch the attributes first and then check the restrictions, so we can spare some time
        // see https://github.com/jolokia/jolokia/issues/893
        Map<String, Object> mapping = new HashMap<>();
        boolean allFetched = false;
        if (attributes.size() > 1) {
            // get all wanted attributes in one call
            try {
                AttributeList allAttributes = getAttributes(jmxAccess, pName, attributes.toArray(String[]::new));
                for (Attribute a : allAttributes.asList()) {
                    mapping.put(a.getName(), a.getValue());
                }
                allFetched = attributes.size() == mapping.size();
            } catch (RuntimeException ignored) {
                // exceptions like InstanceNotFoundException are simply rethrown, but RuntimeExceptions
                // should be handled differently - for example there's
                // javax.management.MBeanServer interceptor for RBAC installed by a custom
                // javax.management.MBeanServerBuilder and we can't be sure which exceptions are thrown
                // (for example Artemis MBean interceptor throws java.lang.SecurityException)
                // in that case we fail at getting all the attributes, so we switch to checking each attribute
                // at a time to collect individual errors using
                // org.jolokia.core.service.serializer.ValueFaultHandler.handleException()
            }
        }

        for (String attribute : attributes) {
            try {
                checkRestriction(pName, attribute);
                if (allFetched) {
                    // no need to fetch it again!
                    result.put(attribute, mapping.get(attribute));
                } else {
                    if (mapping.containsKey(attribute)) {
                        // we can use it, even if it's null
                        result.put(attribute, mapping.get(attribute));
                    } else {
                        // we have to fetch it individually to get the actual exception because
                        // not fetched attribute with getAttributes() is simply ignored
                        result.put(attribute, getAttribute(jmxAccess, pName, attribute));
                    }
                }
            } catch (IOException e) {
                result.put(attribute, pFaultHandler.handleException(e));
            } catch (JMException e) {
                // The fault handler might to decide to rethrow the
                // exception in which case nothing is put extra into result.
                // Otherwise, the replacement value as returned by the
                // fault handler is inserted.
                result.put(attribute, pFaultHandler.handleException(e));
            } catch (RuntimeException e) {
                if (e.getCause() instanceof UnsupportedOperationException unsupportedOperationException) {
                    result.put(attribute, pFaultHandler.handleException(unsupportedOperationException));
                } else {
                    result.put(attribute, pFaultHandler.handleException(e));
                }
            }
        }

        return result;
    }

    /**
     * Jolokia-specific check for attribute permissions
     * @param mBeanName
     * @param attribute
     */
    private void checkRestriction(ObjectName mBeanName, String attribute) {
        if (!context.isAttributeReadAllowed(mBeanName,attribute)) {
            throw new SecurityException("Reading attribute " + attribute + " is forbidden for MBean " + mBeanName.getCanonicalName());
        }
    }

    /**
     * Return matching {@link ObjectName MBean names}. We expect non-empty result, because READ handler needs
     * something to read attributes from. {@link InstanceNotFoundException} is thrown otherwise.
     *
     * @param pServerManager
     * @param pObjectName
     * @return
     * @throws IOException
     * @throws InstanceNotFoundException
     */
    private Set<ObjectName> searchMBeans(MBeanServerAccess pServerManager, ObjectName pObjectName)
            throws IOException, InstanceNotFoundException {
        Set<ObjectName> names = pServerManager.queryNames(pObjectName);
        if (names.isEmpty()) {
            throw new InstanceNotFoundException("No MBean with pattern " + pObjectName + " found for READ request");
        }
        return names;
    }

    /**
     * This method returns reconciled list of attribute names to get from an MBean. While
     * {@link MBeanServerConnection#getAttributes} explicitly says that the number of returned attributes
     * may be less than the number of requested attributes, this method and this entire handler is NOT an implementation
     * of this contract! In actual Jolokia implementation of {@link MBeanServerConnection} ({@code jolokia-client-jmx-adapter})
     * we control this behavior with {@link org.jolokia.server.core.config.ConfigKey#IGNORE_ERRORS}).
     *
     * @param jmxAccess
     * @param pName
     * @param pRequest
     * @param pMode
     * @param pFaultHandler
     * @return
     * @throws IOException
     * @throws JMException
     */
    private List<String> resolveAttributes(MBeanServerAccess jmxAccess, ObjectName pName, JolokiaReadRequest pRequest, ResolveMode pMode, ValueFaultHandler pFaultHandler)
            throws IOException, JMException {

        List<String> attributes = pRequest.isMultiAttributeMode()
            ? pRequest.getAttributeNames() : Collections.singletonList(pRequest.getAttributeName());

        if (pMode == ResolveMode.ALL_REQUESTED && !attributes.isEmpty()) {
            // return all that were asked
            return attributes;
        }

        List<String> allAvailable = getMBeanAttributeNames(jmxAccess, pName);
        if (attributes.isEmpty()) {
            // we want all - not necessarily we WILL fetch all (RBAC?) but that's to be handled by the caller
            return allAvailable;
        }

        // filter and check
        Set<String> available = new LinkedHashSet<>(allAvailable);
        List<String> result = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String name : attributes) {
            if (available.contains(name)) {
                // in strict mode we let the unknown attribute to be fetched (which should end with an exception)
                result.add(name);
            } else {
                unknown.add(name);
            }
        }

        if (pMode == ResolveMode.STRICT && !unknown.isEmpty()) {
            pFaultHandler.handleException(new AttributeNotFoundException("MBean " + pName + " doesn't contain required attributes: " + String.join(", ", unknown)));
        }

        return result;
    }

    /**
     * Get available MBean attribute names from {@link MBeanInfo}
     * @param jmxAccess
     * @param pName
     * @return
     * @throws IOException
     * @throws JMException
     */
    private List<String> getMBeanAttributeNames(MBeanServerAccess jmxAccess, ObjectName pName)
            throws IOException, JMException {
        MBeanInfo mBeanInfo = jmxAccess.call(pName, MBEAN_INFO_ACTION);
        List<String> ret = new ArrayList<>();
        for (MBeanAttributeInfo attrInfo : mBeanInfo.getAttributes()) {
            if (attrInfo.isReadable()) {
                ret.add(attrInfo.getName());
            }
        }
        return ret;
    }

    // Try multiple servers for fetching an attribute

    /**
     * Get value of a selected MBean's attribute
     *
     * @param pServerManager
     * @param pMBeanName
     * @param attribute
     * @return
     * @throws IOException
     * @throws JMException
     */
    private Object getAttribute(MBeanServerAccess pServerManager, ObjectName pMBeanName, String attribute)
            throws IOException, JMException {
        return pServerManager.call(pMBeanName, MBEAN_ATTRIBUTE_READ_HANDLER, attribute);
    }

    /**
     * Get values of selected MBean attributes in one {@link MBeanServerConnection#getAttributes(ObjectName, String[])}
     * call
     *
     * @param jmxAccess
     * @param pName
     * @param attributes
     * @return
     * @throws IOException
     * @throws JMException
     */
    private AttributeList getAttributes(MBeanServerAccess jmxAccess, ObjectName pName, String[] attributes)
            throws IOException, JMException {
        return jmxAccess.call(pName, MBEAN_ATTRIBUTES_READ_HANDLER, (Object[]) attributes);
    }

    /**
     * {@link org.jolokia.server.core.util.jmx.MBeanServerAccess.MBeanAction} to get an {@link MBeanInfo} for
     * an {@link ObjectName}.
     */
    private static final MBeanServerAccess.MBeanAction<MBeanInfo> MBEAN_INFO_ACTION =
        new MBeanServerAccess.MBeanAction<>() {
            @Override
            public MBeanInfo execute(MBeanServerConnection pConn, ObjectName pName, Object... extraArgs)
                    throws IOException, ReflectionException, InstanceNotFoundException, IntrospectionException {
                return pConn.getMBeanInfo(pName);
            }
        };

    /**
     * {@link org.jolokia.server.core.util.jmx.MBeanServerAccess.MBeanAction} to call
     * {@link MBeanServerConnection#getAttribute(ObjectName, String)}
     */
    private static final MBeanServerAccess.MBeanAction<Object> MBEAN_ATTRIBUTE_READ_HANDLER =
        new MBeanServerAccess.MBeanAction<>() {
            @Override
            public Object execute(MBeanServerConnection pConn, ObjectName pName, Object... extraArgs)
                    throws IOException, ReflectionException, InstanceNotFoundException, MBeanException, AttributeNotFoundException {
                String attribute = (String) extraArgs[0];
                return pConn.getAttribute(pName, attribute);
            }
        };

    /**
     * {@link org.jolokia.server.core.util.jmx.MBeanServerAccess.MBeanAction} to call
     * {@link MBeanServerConnection#getAttributes(ObjectName, String[])}
     */
    private static final MBeanServerAccess.MBeanAction<AttributeList> MBEAN_ATTRIBUTES_READ_HANDLER =
        new MBeanServerAccess.MBeanAction<>() {
            @Override
            public AttributeList execute(MBeanServerConnection pConn, ObjectName pName, Object... extraArgs)
                    throws IOException, ReflectionException, InstanceNotFoundException {
                return pConn.getAttributes(pName, (String[]) extraArgs);
            }
        };

    /**
     * When {@link JolokiaReadRequest} asks for attributes, we may have several cases:<ul>
     *     <li>no attribute specified = get all</li>
     *     <li>one attribute specified = we require it to be available, but we let
     *     {@link MBeanServerConnection#getAttribute} fail</li>
     *     <li>a collection (even 1-element one) specified - for {@link ObjectName#isPattern()} we filter by available
     *     and for non-pattern we allow non-existing attributes</li>
     * </ul>
     */
    private enum ResolveMode {
        /** Fail if attribute(s) not available in {@link javax.management.MBeanInfo} */
        STRICT,
        /** Ignore {@link javax.management.MBeanInfo} */
        ALL_REQUESTED,
        /** Filter by available attributes from {@link javax.management.MBeanInfo} */
        FILTERED
    }

}
