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

import java.util.Collections;
import java.util.List;

import org.json.simple.JSONObject;

/**
 * Request for version and server information
 *
 * @author roland
 * @since Apr 24, 2010
 */
public class J4pVersionRequest extends J4pRequest {

    /**
     * Plain Constructor
     */
    public J4pVersionRequest() {
        this(null);
    }

    /**
     * Constructor with using a proxy configuration
     * @param pConfig proxy configuration for a JSR-160 proxy
     */
    public J4pVersionRequest(J4pTargetConfig pConfig) {
        super(J4pType.VERSION,pConfig);
    }

    /** {@inheritDoc} */
    @Override
    List<String> getRequestParts() {
        return Collections.emptyList();
    }

    /** {@inheritDoc} */
    @Override
    J4pVersionResponse createResponse(JSONObject pResponse) {
        return new J4pVersionResponse(this,pResponse);
    }

}
