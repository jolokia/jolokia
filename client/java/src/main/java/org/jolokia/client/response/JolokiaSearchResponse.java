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
package org.jolokia.client.response;

import java.util.ArrayList;
import java.util.List;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.client.request.JolokiaSearchRequest;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;

/**
 * Response for a {@link JolokiaSearchRequest}. It is simply an array of object names.
 *
 * @author roland
 * @since 26.03.11
 */
public final class JolokiaSearchResponse extends JolokiaResponse<JolokiaSearchRequest> {

    public JolokiaSearchResponse(JolokiaSearchRequest pRequest, JSONObject pJsonResponse) {
        super(pRequest, pJsonResponse);
    }

    /**
     * Get the found names as a list of {@link ObjectName} objects.
     *
     * @return list of MBean object names or an empty list.
     */
    public List<ObjectName> getObjectNames() {
        JSONArray names = getMBeanNames();
        List<ObjectName> ret = new ArrayList<>(names.size());

        for (Object name : names) {
            if (name instanceof String oName) {
                try {
                    ret.add(new ObjectName(oName));
                } catch (MalformedObjectNameException e) {
                    // Should never happen since the names returned by the server must
                    // be valid ObjectNames for sure
                    throw new IllegalStateException("Cannot convert search result '" + name + "' to an ObjectName", e);
                }
            } else {
                throw new IllegalStateException("Cannot convert search result '" + name
                    + "' to an ObjectName. Wrong type: " + name.getClass().getName());
            }
        }

        return ret;
    }

    /**
     * Return the list of MBean names as strings
     *
     * @return list of MBean names or an empty list
     */
    public JSONArray getMBeanNames() {
        Object v = getValue();
        if (v instanceof JSONArray array) {
            return array;
        }

        throw new IllegalStateException("Search response is not an array of object names. Found " + v.getClass().getName());
    }

}
