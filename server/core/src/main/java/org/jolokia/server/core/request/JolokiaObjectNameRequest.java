package org.jolokia.server.core.request;

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

import java.util.List;
import java.util.Map;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.util.ProviderUtil;
import org.jolokia.server.core.util.RequestType;
import org.json.simple.JSONObject;

/**
 * Abstract Jolokia request which takes an object name.
 *
 * @author roland
 * @since 15.03.11
 */
public abstract class JolokiaObjectNameRequest extends JolokiaRequest {

    // object name without provider
    private ObjectName objectName;

    // provider for this request (since 2.0)
    private String provider;

    /**
     * Constructor for GET requests
     *
     * @param pType request type
     * @param pName object name, which must not be null.
     * @param pPathParts parts of an path
     * @param pProcessingParams optional init params
     * @param pExclusive  whether the request is an 'exclusive' request or not handled by a single handler only
     * @throws MalformedObjectNameException if the given MBean name is not a valid object name
     */
    protected JolokiaObjectNameRequest(RequestType pType, String pName, List<String> pPathParts,
                                       ProcessingParameters pProcessingParams, boolean pExclusive)
            throws MalformedObjectNameException {
        super(pType,pPathParts,pProcessingParams,pExclusive);
        initObjectName(pName);
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     * @param pExclusive  whether the request is an 'exclusive' request or not handled by a single handler only
     * @throws MalformedObjectNameException if the given name (key: "name")
     *        is not a valid object name (with the provider part removed if given).
     */
    protected JolokiaObjectNameRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams, boolean pExclusive)
            throws MalformedObjectNameException {
        super(pRequestMap, pParams, pExclusive);
        initObjectName((String) pRequestMap.get("mbean"));
    }


    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public JSONObject toJSON() {
        JSONObject ret = super.toJSON();
        ret.put("mbean",getOrderedObjectName(objectName));
        if (provider != null) {
            ret.put("provider", provider);
        }
        return ret;
    }

    @Override
    protected String getInfo() {
        StringBuffer ret = new StringBuffer("objectName = ").append(objectName.getCanonicalName());
        if (provider != null) {
            ret.append(", provider = ").append(provider);
        }
        String baseInfo = super.getInfo();
        if (baseInfo != null) {
            ret.append(", ").append(baseInfo);
        }
        return ret.toString();
    }

    /**
     * Get the object name
     *
     * @return the object name
     */
    public ObjectName getObjectName() {
        return objectName;
    }

    /**
     * Get the provider to be used with this request
     *
     * @return provider
     */
    public String getProvider() {
        return provider;
    }

    /**
     * String representation of the object name for this request.
     *
     * @return the object name a string representation
     */
    public String getObjectNameAsString() {
        return objectName.getCanonicalName();
    }

    /**
     * Name prepared according to requested formatting note. The key ordering can be influenced by the
     * processing parameter {@link ConfigKey#CANONICAL_NAMING}. If not given or set to "true",
     * then the canonical order is used, if set to "initial" the name is given to construction time
     * is used.
     *
     * @param pName name to format
     * @return formatted string
     */
    public String getOrderedObjectName(ObjectName pName) {
        // For patterns we always return the canonical name
        if (pName.isPattern()) {
            return pName.getCanonicalName();
        }
        if (getParameterAsBool(ConfigKey.CANONICAL_NAMING)) {
            return pName.getCanonicalName();
        } else {
            return pName.getDomain() + ":" + pName.getKeyPropertyListString();
        }
    }

    private void initObjectName(String pObjectName) throws MalformedObjectNameException {
        ProviderUtil.ProviderObjectNamePair pair = ProviderUtil.extractProvider(pObjectName);
        provider = pair.getProvider();
        objectName = pair.getObjectName();
    }
}
