package org.jolokia.client.request;

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

import java.util.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.json.simple.JSONObject;

/**
 * Response for a {@link org.jolokia.client.request.J4pType#READ} request. Since a single
 * READ request can result in multiple values returned, this response object
 * allows for obtaining iteration of those values in case the MBean name given for the request
 * was a pattern of when multiple attributes were requested.
 *
 * @author roland
 * @since Apr 26, 2010
 */
public final class J4pReadResponse extends J4pResponse<J4pReadRequest> {

    /**
     * Constructor, which should be used directly.
     *
     * @param pRequest the request which lead to this response.
     * @param pJsonResponse the JSON response as obtained from the server agent.
     */
    J4pReadResponse(J4pReadRequest pRequest, JSONObject pJsonResponse) {
        super(pRequest, pJsonResponse);
    }

    /**
     * Get all MBean names for which the request fetched values. If the request
     * contained an MBean pattern then all MBean names matching this pattern and which contained
     * attributes of the given name are returned. If the MBean wasnt a pattern a single
     * value collection with the single MBean name of the request is returned.
     *
     * @return list of MBean names
     * @throws MalformedObjectNameException if the returned MBean names could not be converted to
     *                                      {@link ObjectName}s. Shouldnt occur, though.
     */
    public Collection<ObjectName> getObjectNames() throws MalformedObjectNameException {
        ObjectName mBean = getRequest().getObjectName();
        if (mBean.isPattern()) {
            // The result value contains the list of fetched object names
            JSONObject values = getValue();
            Set<ObjectName> ret = new HashSet<ObjectName>();
            for (Object name : values.keySet()) {
                ret.add(new ObjectName((String) name));
            }
            return ret;
        } else {
            return Arrays.asList(mBean);
        }
    }

    /**
     * Get the name of all attributes fetched for a certain MBean name. If the request was
     * performed for a single MBean, then the given name must match that of the MBean name
     * provided in the request. If <code>null</code> is given as argument, then this method
     * will return all attributes for the single MBean given in the request
     *
     * @param pObjectName MBean for which to get the attribute names,
     * @return a collection of attribute names
     */
    public Collection<String> getAttributes(ObjectName pObjectName) {
        ObjectName requestMBean = getRequest().getObjectName();
        if (requestMBean.isPattern()) {
            // We need to got down one level in the returned values
            JSONObject attributes = getAttributesForObjectNameWithPatternRequest(pObjectName);
            return attributes.keySet();
        } else {
            if (pObjectName != null && !pObjectName.equals(requestMBean)) {
                throw new IllegalArgumentException("Given ObjectName " + pObjectName + " doesn't match with" +
                        " the single ObjectName " + requestMBean + " given in the request");
            }
            return getAttributes();
        }
    }

    /**
     * Get all attributes obtained. This method can be only used, if the requested MBean
     * was not a pattern (i.e. the request was for a single MBean).
     *
     * @return a list of attributes for this request. If the request was performed for
     *         only a single attribute, the attribute name of the request is returend as
     *         a single valued list. For more than one attribute, the attribute names
     *         a returned from the returned list.
     */
    public Collection<String> getAttributes() {
        J4pReadRequest request = getRequest();
        ObjectName requestBean = request.getObjectName();
        if (requestBean.isPattern()) {
            throw new IllegalArgumentException(
                    "Attributes can be fetched only for non-pattern request (current: " +
                            requestBean.getCanonicalName() + ")");
        }
        // The attribute names are the same as from the request
        if (request.hasSingleAttribute()) {
            // Contains only a single attribute:
            return request.getAttributes();
        } else {
            JSONObject attributes = getValue();
            return attributes.keySet();
        }
    }

    /**
     * Get the value for a certain MBean and a given attribute. This method is especially
     * useful if the request leading to this response was done for multiple MBeans (i.e.
     * a read for an MBean pattern) and multiple attributes. However, this method can be
     * used for a request for single MBeans and single attributes as well, but then the given
     * parameters must match the parameters given in the request.
     *
     * @param pObjectName name of the Mbean or <code>null</code> if the request was only for a single
     *        Mbeans in which case this single MBean is taken from the request
     * @param pAttribute the attribute or <code>null</code> if the request was for a single
     *        attribute in which case the attribute name is taken from the request
     * @param <V> the object type of the return value ({@link String},{@link Map} or {@link List})
     * @return the value
     * @throws IllegalArgumentException if there was no value for the given parameters or if <code>null</code>
     *         was given for given for one or both arguments and the request was for multiple MBeans
     *         or attributes.
     */
    public <V> V getValue(ObjectName pObjectName,String pAttribute) {
        ObjectName requestMBean = getRequest().getObjectName();
        if (requestMBean.isPattern()) {
            JSONObject mAttributes = getAttributesForObjectNameWithPatternRequest(pObjectName);
            if (!mAttributes.containsKey(pAttribute)) {
                throw new IllegalArgumentException("No attribute " + pAttribute + " for ObjectName " + pObjectName + " returned for" +
                        " the given request");
            }
            return (V) mAttributes.get(pAttribute);
        } else {
            return (V) getValue(pAttribute);
        }
    }

    /**
     * Get the value for a single attribute. This method is appropriate if the request was done for a single
     * MBean (no pattern), but multiple attributes. If it is called for a request with non-pattern MBean
     * and a single attribute, the given attribute must match the attribute of the request. If this method is
     * called with a <code>null</code> argument, then it will return the value if the request was for
     * a single attribute, otherwise it will raise an {@link IllegalArgumentException}
     *
     * @param pAttribute attribute for which to get the value
     * @param <V> value type
     * @return value
     * @throws IllegalArgumentException if the attribute could not be found in the return value or if this method
     * is called with a <code>null</code> argument, but the request leads to multiple attribute return values.
     */
    public <V> V getValue(String pAttribute) {
        J4pReadRequest request = getRequest();
        ObjectName requestBean = request.getObjectName();
        if (requestBean.isPattern()) {
            throw new IllegalArgumentException(
                    "Attributes without ObjectName can be fetched only for non-pattern request (current: " +
                            requestBean.getCanonicalName() + ")");
        }
        // The attribute names are the same as from the request
        if (request.hasSingleAttribute()) {
            // Contains only a single attribute:
            if (pAttribute != null && !pAttribute.equals(request.getAttribute())) {
                throw new IllegalArgumentException("Given attribute " + pAttribute + " doesnt match single attribute " +
                        "given " + request.getAttribute() + " in the request");
            }
            return (V) getValue();
        } else {
            JSONObject attributes = getValue();
            if (pAttribute == null) {
                throw new IllegalArgumentException("Cannot use null-attribute name to fetch a value from a multi-attribute request");
            }
            if (!attributes.containsKey(pAttribute)) {
                throw new IllegalArgumentException("No such key " + pAttribute + " in the set of returned attribute values");
            }
            return (V) attributes.get(pAttribute);
        }
    }

    // ============================================================================================================

    private JSONObject getAttributesForObjectNameWithPatternRequest(ObjectName pObjectName) {
        ObjectName pMBeanFromRequest = getRequest().getObjectName();
        ObjectName objectName = pObjectName == null ? pMBeanFromRequest : pObjectName;
        JSONObject values = getValue();
        JSONObject attributes = (JSONObject) values.get(objectName.getCanonicalName());
        if (attributes == null) {
            throw new IllegalArgumentException("No ObjectName " + objectName + " found in the set of returned " +
                    " ObjectNames for requested pattern " + pMBeanFromRequest);
        }
        return attributes;
    }
}
