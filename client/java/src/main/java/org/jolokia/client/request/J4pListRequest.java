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

import javax.management.ObjectName;

import org.json.simple.JSONObject;

/**
 * Request for list JMX information
 *
 * @author roland
 * @since 26.03.11
 */
public class J4pListRequest extends J4pRequest {

    private List<String> pathElements;

    /**
     * Default constructor to be used when all meta information should
     * be fetched.
     */
    protected J4pListRequest() {
        this(null, (String) null);
    }

    /**
     * Default constructor to be used when all meta information should
     * be fetched.
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     */
    protected J4pListRequest(J4pTargetConfig pTargetConfig) {
        this(pTargetConfig, (String) null);
    }

    /**
     * Constructor using a path to restrict the information
     * returned by the list command
     *
     * @param pPath path into the JSON response. The path <strong>must already be
     *        properly escaped</strong> when it contains slashes or exclamation marks.
     *        You can use {@link #escape(String)} in order to escape a single path element.
     */
    public J4pListRequest(String pPath) {
        this(null, pPath);
    }

    /**
     * Constructor using a path to restrict the information
     * returned by the list command
     *
     * @param pConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pPath path into the JSON response. The path <strong>must already be
 *        properly escaped</strong> when it contains slashes or exclamation marks.
 *        You can use {@link #escape(String)} in order to escape a single path element.
     */
    public J4pListRequest(J4pTargetConfig pConfig, String pPath) {
        super(J4pType.LIST,pConfig);
        pathElements = splitPath(pPath);                
    }

    /**
     * Constructor using a list of path elements to restrict the information
     *
     * @param pPathElements list of path elements. The elements <strong>must not be escaped</strong>
     */
    public J4pListRequest(List<String> pPathElements) {
        this(null, pPathElements);
    }

    /**
     * Constructor using a list of path elements to restrict the information
     *
     * @param pConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pPathElements list of path elements. The elements <strong>must not be escaped</strong>
     */
    public J4pListRequest(J4pTargetConfig pConfig, List<String> pPathElements) {
        super(J4pType.LIST,pConfig);
        pathElements = pPathElements;
    }

    /**
     * Constructor for fetching the meta data of a specific MBean
     *
     * @param pObjectName name of MBean for which to fetch the meta data
     */
    public J4pListRequest(ObjectName pObjectName) {
        this(null, pObjectName);
    }


    /**
     * Constructor for fetching the meta data of a specific MBean
     *
     * @param pConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pObjectName name of MBean for which to fetch the meta data
     */
    public J4pListRequest(J4pTargetConfig pConfig, ObjectName pObjectName) {
        super(J4pType.LIST,pConfig);
        pathElements = new ArrayList<String>();
        pathElements.add(pObjectName.getDomain());
        pathElements.add(pObjectName.getCanonicalKeyPropertyListString());
    }

    @Override
    J4pListResponse createResponse(JSONObject pResponse) {
        return new J4pListResponse(this,pResponse);
    }

    @Override
    List<String> getRequestParts() {
        return pathElements;
    }

    @Override
    JSONObject toJson() {
        JSONObject ret = super.toJson();
        if (pathElements != null) {
            StringBuilder path = new StringBuilder();
            for (int i = 0; i < pathElements.size(); i++) {
                path.append(escape(pathElements.get(i)));
                if (i < pathElements.size() - 1) {
                    path.append("/");
                }
            }
            ret.put("path",path.toString());
        }
        return ret;
    }


}
