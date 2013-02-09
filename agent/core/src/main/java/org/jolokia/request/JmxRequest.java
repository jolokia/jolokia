package org.jolokia.request;

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

import org.jolokia.config.*;
import org.jolokia.converter.json.ValueFaultHandler;
import org.jolokia.util.*;
import org.json.simple.JSONObject;

/**
 * Abstract base class for a JMX request. This is the server side
 * representation of a Jolokia request.
 *
 *
 * @author roland
 * @since 15.03.11
 */
public abstract class JmxRequest {

    // Type of request
    private RequestType type;

    // An optional target configuration
    private ProxyTargetConfig targetConfig = null;

    // Processing configuration for tis request object
    private ProcessingParameters processingConfig;

    // A value fault handler for dealing with exception when extracting values
    private ValueFaultHandler valueFaultHandler;

    // HTTP method which lead to this request. The method is selected dependent on the
    // constructor used.
    private HttpMethod method;

    // Path parts, which are used for selecting parts of the return value
    private List<String> pathParts;

    /**
     * Constructor used for representing {@link HttpMethod#GET} requests.
     *
     * @param pType request type
     * @param pPathParts an optional path, splitted up in parts. Not all requests do handle a path.
     * @param pProcessingParams init parameters provided as query params for a GET request. They are used to
     *                    to influence the processing.
     */
    protected JmxRequest(RequestType pType, List<String> pPathParts, ProcessingParameters pProcessingParams) {
        this(pType, HttpMethod.GET, pPathParts, pProcessingParams);
    }

    /**
     * Constructor used for {@link HttpMethod#POST} requests, which receive a JSON payload.
     *
     * @param pMap map containing requests parameters
     * @param pInitParams optional processing parameters (obtained as query parameters or from within the
     *        JSON request)
     */
    public JmxRequest(Map<String, ?> pMap, ProcessingParameters pInitParams) {
        this(RequestType.getTypeByName((String) pMap.get("type")),
             HttpMethod.POST,
             EscapeUtil.parsePath((String) pMap.get("path")),
             pInitParams);

        Map target = (Map) pMap.get("target");
        if (target != null) {
            targetConfig = new ProxyTargetConfig(target);
        }
    }

    // Common parts of both constructors
    private JmxRequest(RequestType pType, HttpMethod pMethod, List<String> pPathParts, ProcessingParameters pProcessingParams) {
        method = pMethod;
        type = pType;
        pathParts = pPathParts;

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
     * Get the proxy target configuration provided with the request
     *
     * @return the proxy target configuration or null if the the request
     *         is not a proxy based request.
     */
    public ProxyTargetConfig getTargetConfig() {
        return targetConfig;
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
     * Get tha value fault handler, which can be passwed around.
     * @return the value fault handler
     */
    public ValueFaultHandler getValueFaultHandler() {
        return valueFaultHandler;
    }

    /**
     * Textual description of this request containing base information. Can be used in toString() methods
     * of subclasses
     *
     * @return description of this base request
     */
    protected String getInfo() {
        StringBuffer ret = new StringBuffer();
        if (pathParts != null) {
            ret.append(", path=").append(pathParts);
        }
        if (targetConfig != null) {
            ret.append(", target=").append(targetConfig);
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

        if (targetConfig != null) {
            ret.put("target", targetConfig.toJSON());
        }
        if (pathParts != null) {
            try {
                ret.put("path",getPath());
            } catch (UnsupportedOperationException exp) {
                // Happens when request doesnt support paths
            }
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
