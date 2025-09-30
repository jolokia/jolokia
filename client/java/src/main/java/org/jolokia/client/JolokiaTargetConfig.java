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
package org.jolokia.client;

import org.jolokia.json.JSONObject;

/**
 * <p>Configuration for a JSR-160 proxy requests which should be proxied by target Jolokia agent to another JVM
 * which doesn't run Jolokia, but exposes remote JMX connector server.</p>
 *
 * <p>The URI of target JVM should be in the form of {@code service:jmx:[URI for remote JMX Connector Server]}.
 * The only mandatory JSR-160 Remote Connector protocol is based on RMI (see chapter 14 of JMX 1.4 specification).
 * It is implemented in {@link javax.management.remote.rmi.RMIConnector}</p>
 *
 * @param url  Service URL for the ultimate target.
 * @param user Optional user and password
 * @author roland
 * @since 27.12.11
 */
public record JolokiaTargetConfig(String url, String user, String password) {

    /**
     * Constructor
     *
     * @param url      service URL for the target which should be reached via JSR-160
     * @param user     optional user used for JSR-160 authentication
     * @param password password for optional JSR-160 authentication
     */
    public JolokiaTargetConfig {
    }

    /**
     * Get a JSON representation of this target configuration
     *
     * @return JSON representation of the target configuration
     */
    public JSONObject toJson() {
        JSONObject ret = new JSONObject();
        ret.put("url", url);
        if (user != null) {
            ret.put("user", user);
            ret.put("password", password);
        }
        return ret;
    }

}
