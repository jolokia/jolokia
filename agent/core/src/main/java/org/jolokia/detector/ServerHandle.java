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

import java.util.Map;

import javax.management.*;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.request.JmxRequest;
import org.jolokia.util.LogHandler;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Information about the the server product the agent is running in.
 * 
 * @author roland
 * @since 05.11.10
 */
public class ServerHandle {

    // product name of server running
    private String product;

    // version number
    private String version;

    // extra information
    private Map<String,String> extraInfo;

    // vendor name
    private String vendor;

    /**
     * Constructor
     *
     * @param vendor product vendor (like RedHat or Oracle)
     * @param product name of the product
     * @param version version
     * @param extraInfo free form extra information
     */
    public ServerHandle(String vendor, String product, String version, Map<String, String> extraInfo) {
        this.product = product;
        this.version = version;
        this.extraInfo = extraInfo;
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
     * Get extra information specific to a server. A subclass can overwrite this in order
     * to provide dynamice information on the server which gets calculated afresh
     * on each invokation.
     *
     *
     *
     * @param pServerManager MBeanServers to query
     * @return a map of extra info or <code>null</code> if no extra information is given.
     */
    public Map<String,String> getExtraInfo(MBeanServerExecutor pServerManager) {
        return extraInfo;
    }

    /**
     * Hook for performing certain workarounds/pre processing just before
     * a request gets dispatched
     *
     * @param pMBeanServerExecutor the detected MBeanServers
     * @param pJmxReq the request to dispatch
     */
    public void preDispatch(MBeanServerExecutor pMBeanServerExecutor, JmxRequest pJmxReq) {
        // Do nothing
    }

    /**
     * Hook called after the detection phase. This can be used by a handle to perform
     * some specific action, possibly based on the configuration given.
     *
     * The default is a no-op.
     *
     * @param pServerManager
     * @param pConfig agent configuration
     * @param pLoghandler logger to use for logging any error.
     */
    public void postDetect(MBeanServerExecutor pServerManager, Configuration pConfig, LogHandler pLoghandler) {
        // Do nothing
    }

    /**
     * Register a MBean at the dedicated server. This method can be overridden if
     * something special registration procedure is required, like for using the
     * specific name for the registration or deligating the namin to MBean to register.
     *
     * @param pServer server an MBean should be registered
     * @param pMBean the MBean to register
     * @param pName an optional name under which the MBean should be registered. Can be null
     * @return the object name of the registered MBean
     * @throws MBeanRegistrationException when registration failed
     * @throws InstanceAlreadyExistsException when there is already MBean with this name
     * @throws NotCompliantMBeanException
     * @throws MalformedObjectNameException if the name is not valid
     */
    public ObjectName registerMBeanAtServer(MBeanServer pServer, Object pMBean, String pName)
            throws MBeanRegistrationException, InstanceAlreadyExistsException, NotCompliantMBeanException, MalformedObjectNameException {
        if (pName != null) {
            ObjectName oName = new ObjectName(pName);
            return pServer.registerMBean(pMBean,oName).getObjectName();
        } else {
            // Needs to implement MBeanRegistration interface
            return pServer.registerMBean(pMBean,null).getObjectName();
        }
    }

    /**
     * Return this info as an JSONObject
     *
     *
     *
     * @param pServerManager servers, for which dynamic part might be queried
     * @return this object in JSON representation
     */
    public JSONObject toJSONObject(MBeanServerExecutor pServerManager) {
        JSONObject ret = new JSONObject();
        addNullSafe(ret, "vendor", vendor);
        addNullSafe(ret, "product", product);
        addNullSafe(ret, "version", version);
        Map<String,String> extra = getExtraInfo(pServerManager);
        if (extra != null) {
            JSONObject jsonExtra = new JSONObject();
            for (Map.Entry<String,String> entry : extra.entrySet()) {
                jsonExtra.put(entry.getKey(),entry.getValue());
            }
            ret.put("extraInfo", jsonExtra);
        }
        return ret;
    }

    private void addNullSafe(JSONObject pRet, String pKey, Object pValue) {
        if (pValue != null) {
            pRet.put(pKey,pValue);
        }
    }

    /**
     * Get the optional options used for detectors. This should be a JSON string specifying all options
     * for all detectors. Keys are the name of the detector's product, the values are JSON object containing
     * specific parameters for this agent. E.g.
     *
     * <pre>
     *    {
     *        "glassfish" : { "bootAmx": true  }
     *    }
     * </pre>
     *
     *
     * @param pConfig the agent configuration
     * @param pLogHandler a log handler for putting out error messages
     * @return the detector specific configuration
     */
    protected JSONObject getDetectorOptions(Configuration pConfig, LogHandler pLogHandler) {
        String options = pConfig.get(ConfigKey.DETECTOR_OPTIONS);
        try {
            if (options != null) {
                    JSONObject opts = (JSONObject) new JSONParser().parse(options);
                    return (JSONObject) opts.get(getProduct());
            }
            return null;
        } catch (ParseException e) {
            pLogHandler.error("Could not parse detector options '" + options + "' as JSON object: " + e,e);
        }
        return null;
    }
}
