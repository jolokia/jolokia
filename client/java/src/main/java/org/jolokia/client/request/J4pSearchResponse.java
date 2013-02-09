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

import java.util.ArrayList;
import java.util.List;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.json.simple.JSONObject;

/**
 * Response for a {@link J4pSearchRequest}
 *
 * @author roland
 * @since 26.03.11
 */

public final class J4pSearchResponse extends J4pResponse<J4pSearchRequest> {

    J4pSearchResponse(J4pSearchRequest pRequest, JSONObject pJsonResponse) {
        super(pRequest, pJsonResponse);
    }

    /**
     * Get the found names as a list of {@link ObjectName} objects.
     *
     * @return list of MBean object names or an empty list.
     */
    public List<ObjectName> getObjectNames() {
        List<String> names = getMBeanNames();
        List<ObjectName> ret = new ArrayList<ObjectName>(names.size());
        for (String name : names) {
            try {
                ret.add(new ObjectName(name));
            } catch (MalformedObjectNameException e) {
                // Should never happen since the names returned by the server must
                // be valid ObjectNames for sure
                throw new IllegalStateException("Cannot convert search result '" + name + "' to an ObjectName",e);
            }
        }
        return ret;
    }

    /**
     * Return the list of MBean names as strings
     *
     * @return list of MBean names or an empty list
     */
    public List<String> getMBeanNames() {
        return getValue();
    }
}
