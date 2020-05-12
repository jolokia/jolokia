package org.jolokia.client.request;/*
 * 
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

import java.util.HashSet;
import java.util.Set;

import org.jolokia.client.exception.J4pRemoteException;
import org.json.simple.JSONObject;

/**
 * A response extractor which does validation based on Jolokia status codes.
 *
 * @author roland
 * @since 23/12/14
 */
public class ValidatingResponseExtractor implements J4pResponseExtractor {

    /**
     * Extractor which only considers status code 200 as valid
     */
    public final static ValidatingResponseExtractor DEFAULT = new ValidatingResponseExtractor();

    /**
     * Extractor which permits code 200 and 404 (NotFound) as possible values. If 404 is returned it returns an empty
     * object.
     */
    public final static ValidatingResponseExtractor OPTIONAL = new ValidatingResponseExtractor(404);

    Set<Integer> allowedCodes;

    public ValidatingResponseExtractor(int... pCodesAllowed) {
        allowedCodes = new HashSet<Integer>();
        // 200 is always contained
        allowedCodes.add(200);
        for (int code : pCodesAllowed) {
            allowedCodes.add(code);
        }
    }

    public <RESP extends J4pResponse<REQ>, REQ extends J4pRequest> RESP extract(REQ pRequest,
                                                                      JSONObject pJsonResp)
            throws J4pRemoteException {
	
	int status = 0;
	if( pJsonResp.containsKey("status")) {
		Object o = pJsonResp.get("status");
		if( o instanceof Long ) {
			status = ((Long)o).intValue();
		} 
	}

        if (!allowedCodes.contains(status)) {
            throw new J4pRemoteException(pRequest, pJsonResp);
        }
        return status == 200 ? pRequest.<RESP>createResponse(pJsonResp) : null;
    }
}
