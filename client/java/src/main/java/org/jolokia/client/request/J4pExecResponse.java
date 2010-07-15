package org.jolokia.client.request;

import org.json.simple.JSONObject;

/**
 * Response for an execute request
 *
 * @author roland
 * @since May 18, 2010
 */
public final class J4pExecResponse extends J4pResponse<J4pExecRequest> {

    J4pExecResponse(J4pExecRequest pRequest, JSONObject pJsonResponse) {
        super(pRequest, pJsonResponse);
    }
}
