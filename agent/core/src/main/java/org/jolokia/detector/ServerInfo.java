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

import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Information about the the server product the agent is running in.
 * 
 * @author roland
 * @since 05.11.10
 */
public class ServerInfo implements JSONAware {

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

    public ServerInfo(String vendor, String product, String version, URL agentUrl, Map<String, String> extraInfo) {
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
     * Get extra information specific to a server
     * @return a map of extra info or <code>null</code> if no extra information is given.
     */
    public Map<String,String> getExtraInfo() {
        return extraInfo;
    }

    /**
     * Create a JSON representation of this server info
     *
     * @return JSOn representation as string
     */
    public String toJSONString() {
        Map ret = new HashMap();
        ret.put("vendor",vendor);
        ret.put("product",product);
        if (version != null) {
            ret.put("version",version);
        }
        if (agentUrl != null) {
            ret.put("agent-url",agentUrl);
        }
        if (extraInfo != null) {
            ret.put("extra",extraInfo);
        }
        return JSONObject.toJSONString(ret);
    }
}
