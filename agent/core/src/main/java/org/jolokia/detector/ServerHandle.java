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

import java.net.URL;
import java.util.Map;
import java.util.Set;

import javax.management.*;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.config.ConfigKey;
import org.jolokia.request.JmxRequest;
import org.jolokia.service.JolokiaContext;
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
    public static final ServerHandle NULL_SERVER_HANDLE = new ServerHandle(null, null, null, null, null);

    // an unique id of the agent for identifying a
    // Jolokia agent in a JVM (there can be multiple)
    private String jolokiaId;

    // product name of server running
    private String product;

    // version number
    private String version;

    // the agent URL under which this server can be found
    private URL agentUrl;

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
     * @param agentUrl the URL under which the agent is reachable (or null if not detectable)
     * @param extraInfo free form extra information
     */
    public ServerHandle(String vendor, String product, String version, URL agentUrl, Map<String, String> extraInfo) {
        this.product = product;
        this.version = version;
        this.agentUrl = agentUrl;
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
     * URL under which the agent can be reached. Note, that this information
     * is hard to detect, especially when the server is hidden behind some reverse
     * proxy. Hence the result is more a heuristic.
     *
     * @return agent url
     */
    public URL getAgentUrl() {
        return agentUrl;
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
     * Get the Jolokia id unique for this agent
     * @return jolokia id
     */
    public String getJolokiaId() {
        return jolokiaId;
    }

    /**
     * Set the jolokia agent id
     *
     * @param pJolokiaId jolokia agent id
     */
    public void setJolokiaId(String pJolokiaId) {
        jolokiaId = pJolokiaId;
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
     * @param pContext the Jolokia Context
     */
    public void postDetect(MBeanServerExecutor pServerManager, JolokiaContext pContext) {
        // Do nothing
    }

    /**
     * Add MBeanServers dedicated specifically on the identified platform. This method must be overridden
     * by any platform wanting to add MBeanServers. By default this method does nothing.
     *
     * @param pMBeanServers set of MBeanServers to add to.
     */
    public void addMBeanServers(Set<MBeanServerConnection> pMBeanServers) { }

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
        addNullSafe(ret, "agent-url", agentUrl != null ? agentUrl.toExternalForm() : null);
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
}
