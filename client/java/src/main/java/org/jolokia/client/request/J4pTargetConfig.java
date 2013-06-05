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

import org.json.simple.JSONObject;

/**
 * Configuration for a JSR-160 proxy request for specifying the target server.
 * 
 * @author roland
 * @since 27.12.11
 */
public class J4pTargetConfig {

    // Service URL for the final target
    private String url;

    // Optional user and password
    private String user;
    private String password;

    /**
     * Constructor
     * @param pUrl service URL for the target which should be reached via JSR-160
     * @param pUser optional user used for JSR-160 authentication
     * @param pPassword password for optional JSR-160 authentication
     */
    public J4pTargetConfig(String pUrl, String pUser, String pPassword) {
        this.url = pUrl;
        this.user = pUser;
        this.password = pPassword;
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Get a JSON representation of this target configuration
     * 
     * @return JSON representation of the target configuration
     */
    public JSONObject toJson() {
        JSONObject ret = new JSONObject();
        ret.put("url",url);
        if (user != null) {
            ret.put("user",user);
            ret.put("password",password);
        }
        return ret;
    }
}
