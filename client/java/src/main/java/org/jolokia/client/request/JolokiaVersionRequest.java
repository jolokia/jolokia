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
package org.jolokia.client.request;

import java.util.Collections;
import java.util.List;

import org.jolokia.client.JolokiaOperation;
import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.client.response.JolokiaVersionResponse;
import org.jolokia.json.JSONObject;

/**
 * Request for version and server information. This request doesn't use any parameters and paths, so the format
 * of POST message and GET path is the same as documented in {@link JolokiaRequest}.
 *
 * @author roland
 * @since Apr 24, 2010
 */
public class JolokiaVersionRequest extends JolokiaRequest {

    /**
     * Version request without {@link JolokiaTargetConfig}
     */
    public JolokiaVersionRequest() {
        this(null);
    }

    /**
     * Constructor with a {@link JolokiaTargetConfig proxy configuration}
     *
     * @param pConfig proxy configuration for a JSR-160 proxy
     */
    public JolokiaVersionRequest(JolokiaTargetConfig pConfig) {
        super(JolokiaOperation.VERSION, pConfig);
    }

    @Override
    public List<String> getRequestParts() {
        // non-null, empty list - URL is just /version
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public JolokiaVersionResponse createResponse(JSONObject pResponse) {
        return new JolokiaVersionResponse(this, pResponse);
    }

}
