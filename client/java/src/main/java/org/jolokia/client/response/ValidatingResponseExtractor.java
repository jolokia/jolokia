/*
 * Copyright 2014 Roland Huss
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

import java.util.HashSet;
import java.util.Set;

import org.jolokia.client.exception.JolokiaRemoteException;
import org.jolokia.client.request.JolokiaRequest;
import org.jolokia.json.JSONObject;

/**
 * A response extractor which does validation based on Jolokia status codes. This is where {@link JolokiaRequest}
 * is asked to create correct {@link JolokiaResponse} with the {@link JSONObject} content.
 *
 * @author roland
 * @since 23/12/14
 */
public class ValidatingResponseExtractor implements JolokiaResponseExtractor {

    /**
     * Extractor which only considers status code 200 as valid
     */
    public final static ValidatingResponseExtractor DEFAULT = new ValidatingResponseExtractor();

    /**
     * Extractor which permits code 200 and 404 (NotFound) as possible values. If 404 is returned it returns an empty
     * object. (Used for tests)
     */
    public final static ValidatingResponseExtractor OPTIONAL = new ValidatingResponseExtractor(404);

    Set<Integer> allowedCodes;

    /**
     * Create a {@link ValidatingResponseExtractor} with all the HTTP response codes accepted. HTTP 200 is
     * always treated as the expected allowed HTTP response code.
     *
     * @param pCodesAllowed
     */
    public ValidatingResponseExtractor(int... pCodesAllowed) {
        allowedCodes = new HashSet<>();
        // 200 is always contained
        allowedCodes.add(200);
        for (int code : pCodesAllowed) {
            allowedCodes.add(code);
        }
    }

    @Override
    public <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest> RESP extract(REQ pRequest, JSONObject pJsonResp, boolean includeRequest)
            throws JolokiaRemoteException {
        int status = 0;
        if (pJsonResp.containsKey("status")) {
            Object o = pJsonResp.get("status");
            if (o instanceof Number n) {
                status = n.intValue();
            }
        }

        if (!allowedCodes.contains(status)) {
            throw new JolokiaRemoteException(pRequest, pJsonResp);
        }
        if (status == 200) {
            RESP response = pRequest.createResponse(pJsonResp);
            if (!includeRequest) {
                response.clearRequest();
            }
            return response;
        }

        return null;
    }

}
