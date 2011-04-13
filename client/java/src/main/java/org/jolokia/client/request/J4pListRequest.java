/*
 * Copyright 2009-2011 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.client.request;

import java.util.ArrayList;
import java.util.List;

import javax.management.ObjectName;

import org.json.simple.JSONObject;

/**
 * Request for list JMX information
 *
 * @author roland
 * @since 26.03.11
 */
public class J4pListRequest extends J4pRequest {

    private String path;

    /**
     * Default constructor to be used when all meta information should
     * be fetched.
     */
    protected J4pListRequest() {
        this((String) null);
    }

    /**
     * Constructor using a path to restrict the information
     * returned by the list command
     *
     * @param pPath path into the JSON response
     */
    public J4pListRequest(String pPath) {
        super(J4pType.LIST);
        path = pPath;
    }

    /**
     * Constructor for fetching the meta data of a specific MBean
     *
     * @param pObjectName name of MBean for which to fetch the meta data
     */
    public J4pListRequest(ObjectName pObjectName) {
        super(J4pType.LIST);
        path = pObjectName.getDomain() + "/" + pObjectName.getCanonicalKeyPropertyListString();
    }

    @Override
    J4pListResponse createResponse(JSONObject pResponse) {
        return new J4pListResponse(this,pResponse);
    }

    @Override
    List<String> getRequestParts() {
        if (path != null) {
            List<String> ret = new ArrayList<String>();
            addPath(ret,path);
            return ret;
        } else {
            return null;
        }
    }

    @Override
    JSONObject toJson() {
        JSONObject ret = super.toJson();
        if (path != null) {
            ret.put("path",path);
        }
        return ret;
    }
}
