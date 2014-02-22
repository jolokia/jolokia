package org.jolokia.service;

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

import java.util.Map;

import org.jolokia.config.ConfigKey;
import org.jolokia.util.jmx.MBeanServerExecutor;
import org.jolokia.request.JolokiaRequest;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Information about the the server product the agent is running in.
 * 
 * @author roland
 * @since 05.11.10
 */
public class ServerHandle {

    // Empty server handle
    public static final ServerHandle NULL_SERVER_HANDLE = new ServerHandle(null, null, null);

    // product name of server running
    private String product;

    // version number
    private String version;

    // vendor name
    private String vendor;

    /**
     * Constructor
     *
     * @param vendor product vendor (like RedHat or Oracle)
     * @param product name of the product
     * @param version version
     */
    public ServerHandle(String vendor, String product, String version) {
        this.product = product;
        this.version = version;
        this.vendor = vendor;
    }

    /**
     * Get name of vendor
     */
    public String getVendor() {
        return vendor;
    }

    /**
     * Get the name of the server this agent is running in
     *
     * @return server name
     */
    public String getProduct() {
        return product;
    }

    /**
     * Get version number of the agent server
     * @return version number
     */
    public String getVersion() {
        return version;
    }

    /**
     * Hook for performing certain workarounds/pre processing just before
     * a request gets dispatched
     *
     * @param pExecutor a JMX executor for easy JMX access
     * @param pJmxReq the request to dispatch
     */
    public void preDispatch(MBeanServerExecutor pExecutor, JolokiaRequest pJmxReq) {
        // Do nothing
    }

    /**
     * Hook called after the detection phase. This can be used by a handle to perform
     * some specific action, possibly based on the configuration given.
     *
     * The default is a no-op.
     *
     * @param pExecutor JMX executor for allowing easy JMX accessing
     * @param pContext the Jolokia Context
     */
    public void postDetect(MBeanServerExecutor pExecutor, JolokiaContext pContext) {
        // Do nothing
    }

    /**
     * Return this info as an JSONObject
     *
     * @return this object in JSON representation
     */
    public JSONObject toJSONObject() {
        JSONObject ret = new JSONObject();
        addNullSafe(ret, "vendor", vendor);
        addNullSafe(ret, "product", product);
        addNullSafe(ret, "version", version);
        return ret;
    }

    private void addNullSafe(JSONObject pRet, String pKey, Object pValue) {
        if (pValue != null) {
            pRet.put(pKey,pValue);
        }
    }

    /**
     * Get the optional options used for detectors-default. This should be a JSON string specifying all options
     * for all detectors-default. Keys are the name of the detector's product, the values are JSON object containing
     * specific parameters for this agent. E.g.
     *
     * <pre>
     *    {
     *        "glassfish" : { "bootAmx": true  }
     *    }
     * </pre>
     *
     *
     * @param pCtx the jolokia context
     * @return the detector specific configuration
     */
    protected JSONObject getDetectorOptions(JolokiaContext pCtx) {
        String optionString = pCtx.getConfig(ConfigKey.DETECTOR_OPTIONS);
        if (optionString != null) {
            try {
                JSONObject opts = (JSONObject) new JSONParser().parse(optionString);
                return (JSONObject) opts.get(getProduct());
            } catch (Exception e) {
                pCtx.error("Could not parse options '" + optionString + "' as JSON object: " + e, e);
            }
        }
        return null;
    }

    /**
     * Extract extra dynamic information specific for this server handle. It can be obtained
     * from JMX if necessary and hence an server executor is given for enabling a JMX query.
     * A subclass should override this since this default method returns null.
     *
     * @param pServerManager server manager for allowing a query
     * @return extra information
     */
    public Map<String, String> getExtraInfo(MBeanServerExecutor pServerManager) {
        return null;
    }
}
