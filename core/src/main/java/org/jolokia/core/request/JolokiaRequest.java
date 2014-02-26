package org.jolokia.core.request;

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

import org.jolokia.core.config.ConfigKey;
import org.jolokia.core.service.serializer.ValueFaultHandler;
import org.jolokia.core.util.*;
import org.json.simple.JSONObject;

/**
 * Abstract base class for a JMX request. This is the server side
 * representation of a Jolokia request.
 *
 * @author roland
 * @since 15.03.11
 */
public abstract class JolokiaRequest {

    // Type of request
    private RequestType type;

    // Processing configuration for tis request object
    private ProcessingParameters processingConfig;

    // A value fault handler for dealing with exception when extracting values
    private ValueFaultHandler valueFaultHandler;

    // HTTP method which lead to this request. The method is selected dependent on the
    // constructor used.
    private HttpMethod method;

    // Path parts, which are used for selecting parts of the return value
    private List<String> pathParts;

    // Free-form options
    private JSONObject options = null;

    // 'exclusive' (like 'read') or not (like 'list')
    private boolean exclusive = true;

    /**
     * Constructor used for representing {@link HttpMethod#GET} requests.
     *
     * The final parameter <code>pExclusive</code> decides whether this request
     * is an 'exclusive' request, i.e. whether the processing is done exclusively by
     * a handler or whether multiple requests handlers can add to the result.
     * Good examples are "list" requests which collects the results from multiple request
     * handlers whereas "read" only reads the values from a single requesthandler
     *
     * @param pType request type
     * @param pPathParts an optional path, split up in parts. Not all requests do handle a path.
     * @param pProcessingParams init parameters provided as query params for a GET request. They are used to
     *                    to influence the processing.
     * @param pExclusive whether the request is an 'exclusive' request or not.
     */
    protected JolokiaRequest(RequestType pType, List<String> pPathParts, ProcessingParameters pProcessingParams, boolean pExclusive) {
        this(pType, HttpMethod.GET, pPathParts, pProcessingParams,pExclusive);
    }

    /**
     * Constructor used for {@link HttpMethod#POST} requests, which receive a JSON payload.
     *
     * @param pMap map containing requests parameters§
     * @param pProcessingParams optional processing parameters (obtained as query parameters or from within the
     *        JSON request)
     * @param pExclusive  whether the request is an 'exclusive' request or not handled by a single handler only
     */
    protected JolokiaRequest(Map<String, ?> pMap, ProcessingParameters pProcessingParams,boolean pExclusive) {
        this(RequestType.getTypeByName((String) pMap.get("type")),
             HttpMethod.POST,
             EscapeUtil.parsePath((String) pMap.get("path")),
             pProcessingParams,
             pExclusive);

        JSONObject reqOptions = (JSONObject) pMap.get("options");
        if (reqOptions != null) {
            options = reqOptions;
        }

        updateForLegacyProxyConfiguration(pMap);
    }

    // For backwards compatibility, examine "target" as well
    private void updateForLegacyProxyConfiguration(Map<String, ?> pMap) {
        JSONObject targetOptions = (JSONObject) pMap.get("target");
        if (targetOptions != null) {
            if (options == null) {
                options = new JSONObject();
            }
            options.put("target",targetOptions);
            options.put("targetId",targetOptions.get("url"));
        }
    }

    // Common parts of both constructors
    private JolokiaRequest(RequestType pType, HttpMethod pMethod, List<String> pPathParts, ProcessingParameters pProcessingParams, boolean pExclusive) {
        method = pMethod;
        type = pType;
        pathParts = pPathParts;
        exclusive = pExclusive;

        initParameters(pProcessingParams);
    }

    /**
     * Get request type
     *
     * @return request type of this request.
     */
    public RequestType getType() {
        return type;
    }

    /**
     * Get a processing configuration or null if not set
     * @param pConfigKey configuration key to fetch
     * @return string value or <code>null</code> if not set
     */
    public String getParameter(ConfigKey pConfigKey) {
        return processingConfig.get(pConfigKey);
    }

    /**
     * Get a processing configuration as integer or null
     * if not set
     *
     * @param pConfigKey configuration to lookup
     * @return integer value of configuration or 0 if not set.
     */
    public int getParameterAsInt(ConfigKey pConfigKey) {
        String intValueS = processingConfig.get(pConfigKey);
        if (intValueS != null) {
            return Integer.parseInt(intValueS);
        } else {
            return 0;
        }
    }

    /**
     * Get a processing configuration as a boolean value
     *
     * @param pConfigKey configuration to lookup
     * @return boolean value of the configuration, the default value or false if the default value is null
     */
    public Boolean getParameterAsBool(ConfigKey pConfigKey) {
        String booleanS = getParameter(pConfigKey);
        return Boolean.parseBoolean(booleanS != null ? booleanS : pConfigKey.getDefaultValue());
    }

    /**
     * Get an option from the request. Options are used to help request dispatchers in serving
     * a request for a specific realm. E.g. the Jsr160 request dispatchers uses this in order to
     * obtain the JSR-160 target information.
     *
     * @param pKey get the option for this key
     * @return the specificied option or or null if no such option was set
     */
    public <T> T getOption(String pKey) {
        return options != null ? (T) options.get(pKey) : null;
    }


    /**
     * Whether this request is an exclusive request or not. See {@link #JolokiaRequest(RequestType, List, ProcessingParameters, boolean)}
     * for details
     *
     * @return true if this is a request handled by a single handler.
     */
    public boolean isExclusive() {
        return exclusive;
    }

    /**
     * HTTP method used for creating this request
     *
     * @return HTTP method which lead to this request
     */
    public HttpMethod getHttpMethod() {
        return method;
    }

    /**
     * Get tha value fault handler, which can be passed around.
     * @return the value fault handler
     */
    public ValueFaultHandler getValueFaultHandler() {
        return valueFaultHandler;
    }

    /**
     * Whether a return value should be returned directly, ignoring any path. In this
     * case this method should return false.
     *
     * By default, a path needs to be applied to the returned value afterwards in order to
     * get the desired value.
     *
     * @return true if the path within the request should be respected when generating the answer later,
     *         false if the value should be directly returned
     */
    public boolean useReturnValueWithPath() {
        return true;
    }


    /**
     * Textual description of this request containing base information. Can be used in toString() methods
     * of subclasses.
     *
     * @return description of this base request
     */
    protected String getInfo() {
        StringBuilder ret = new StringBuilder();
        if (pathParts != null) {
            ret.append(", path=").append(pathParts);
        }
        if (options != null) {
            ret.append(", options={");
            for (Map.Entry entry : (Set<Map.Entry>) options.entrySet()) {
                ret.append(entry.getKey()).append("=").append(entry.getValue());
            }
            ret.append("}");
        }
        return ret.length() > 0 ? ret.toString() : null;
    }

    /**
     * Get the parts of a path
     *
     * @return the parts of an path or null if no path was used
     */
    public List<String> getPathParts() {
        return pathParts;
    }

    /**
     * Get the path combined with proper escaping
     *
     * @return path as string or null if no path is given.
     */
    public String getPath() {
        return EscapeUtil.combineToPath(pathParts);
    }

    /**
     * Convert this request to a JSON object, which can be used as a part of a return value.
     *
     * @return JSON object representing this base request object
     */
    public JSONObject toJSON() {
        JSONObject ret = new JSONObject();
        ret.put("type",type.getName());

        if (options != null) {
            ret.put("options", options);
        }
        if (pathParts != null) {
            ret.put("path",getPath());
        }
        return ret;
    }

    // Init parameters and value fault handler
    private void initParameters(ProcessingParameters pParams) {
        processingConfig = pParams;
        String ignoreErrors = processingConfig.get(ConfigKey.IGNORE_ERRORS);
        if (ignoreErrors != null && ignoreErrors.matches("^(true|yes|on|1)$")) {
            valueFaultHandler = ValueFaultHandler.IGNORING_VALUE_FAULT_HANDLER;
        } else {
            valueFaultHandler = ValueFaultHandler.THROWING_VALUE_FAULT_HANDLER;
        }
    }

}
