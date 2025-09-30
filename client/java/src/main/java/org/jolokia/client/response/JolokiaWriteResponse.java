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

import org.jolokia.client.request.JolokiaWriteRequest;
import org.jolokia.json.JSONObject;

/**
 * Response for a {@link JolokiaWriteRequest}. As value it returns the old value of the
 * attribute, potentially extracted information using "path" passed with the request. There are no special
 * methods (unlike as in {@link JolokiaReadResponse} for getting the value exception base
 * {@link JolokiaResponse#getValue()}
 *
 * @author roland
 * @since Jun 5, 2010
 */
public final class JolokiaWriteResponse extends JolokiaResponse<JolokiaWriteRequest> {

    public JolokiaWriteResponse(JolokiaWriteRequest pRequest, JSONObject pJsonResponse) {
        super(pRequest, pJsonResponse);
    }

}
