/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.server.core.service.api;

import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;

/**
 * Similar to {@link AgentDetails} but for exposing "just enough" information about supported authentication
 * mechanisms to be available at new {@code /config} endpoint.
 */
public class SecurityDetails {

    private final JSONObject details;
    private final JSONArray methods;

    public SecurityDetails() {
        details = new JSONObject();
        methods = new JSONArray();
        details.put("authentication", methods);
    }

    public void registerAuthenticationMethod(AuthMethod method, String realm) {
        // I know that realm is usually specific to BASIC authentication, but we can tweak this later
        JSONObject m = new JSONObject();
        m.put("method", method.method());
        if (realm != null && !realm.isEmpty()) {
            m.put("realm", realm);
        }
        methods.add(m);
    }

    public JSONObject toJSONObject() {
        return details;
    }

    /**
     * Enumeration of all supported authentication methods Jolokia can handle or can inform about without
     * providing too many details
     */
    public enum AuthMethod {
        BASIC("basic"),
        MTLS("mtls");

        private final String method;

        AuthMethod(String method) {
            this.method = method;
        }

        public String method() {
            return method;
        }
    }

}
