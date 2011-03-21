/*
 * Copyright 2009-2010 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.detector;

import java.net.URL;
import java.util.Map;
import java.util.Set;

import javax.management.*;

import org.jolokia.request.JmxRequest;
import org.json.simple.JSONObject;

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

    // the aent URL under which this server can be found
    private URL agentUrl;

    // extra information
    private Map<String,String> extraInfo;

    // vendor name
    private String vendor;

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
     * @param pServers MBeanServers to query
     * @return a map of extra info or <code>null</code> if no extra information is given.
     */
    public Map<String,String> getExtraInfo(Set<? extends MBeanServerConnection> pServers) {
        return extraInfo;
    }

    /**
     * Hook for performing certain workarounds/pre processing just before
     * a request gets dispatched
     *
     * @param pMBeanServers the detected MBeanServers
     * @param pJmxReq the request to dispatch
     */
    public void preDispatch(Set<MBeanServer> pMBeanServers, JmxRequest pJmxReq) {
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
     * @param pServers servers, for which dynamic part might be queried
     * @return this object in JSON representation
     */
    public JSONObject toJSONObject(Set<MBeanServerConnection> pServers) {
        JSONObject ret = new JSONObject();
        addNullSafe(ret, "vendor", vendor);
        addNullSafe(ret, "product", product);
        addNullSafe(ret, "version", version);
        addNullSafe(ret, "agent-url", agentUrl);
        addNullSafe(ret, "extraInfo", getExtraInfo(pServers));
        return ret;
    }

    private void addNullSafe(JSONObject pRet, String pKey, Object pValue) {
        if (pValue != null) {
            pRet.put(pKey,pValue);
        }
    }


}
