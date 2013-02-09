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

import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;

/**
 * Configuration for proxy mode
 *
 * @author roland
 * @since 15.03.11
 */
public class ProxyTargetConfig {
    private String url;
    private Map<String,String> env;

    /**
     * Map which should contain the following keys
     *
     * <ul>
     *     <li><code>url</code> -- JSR-160 Url of the target (mandatory)</li>
     *     <li><code>user</code> and <code>password</code> -- user and password to use (optional)</li>
     * </ul>
     * @param pMap map containing configuration
     */
    public ProxyTargetConfig(Map<String,String> pMap) {
        url = pMap.get("url");
        if (url == null) {
            throw new IllegalArgumentException("No service url given for JSR-160 target");
        }
        String user = pMap.get("user");
        if (user != null) {
            env = new HashMap<String, String>();
            env.put("user",user);
            String pwd = pMap.get("password");
            if (pwd != null) {
                env.put("password",pwd);
            }
        }
    }

    /**
     * JSR-160 service URL
     *
     * @return Remote service URL of the target
     */
    public String getUrl() {
        return url;
    }

    /**
     * Env with user and password or empty map
     *
     * @return environment map
     */
    public Map<String, String> getEnv() {
        return env;
    }

    /**
     * As JSON representation
     *
     * @return JSON object representing this proxy configuration
     */
    public JSONObject toJSON() {
        JSONObject ret = new JSONObject();
        ret.put("url", url);
        if (env != null) {
            ret.put("env", env);
        }
        return ret;
    }

    @Override
    public String toString() {
        return "TargetConfig[" +
                url +
                ", " + env +
                "]";
    }
}
