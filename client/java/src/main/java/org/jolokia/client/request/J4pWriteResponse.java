package org.jolokia.client.request;

import org.json.simple.JSONObject;

/**
 * Response for a {@link J4pWriteRequest}. As value it returns the old value of the
 * attribute.
 *
 * @author roland
 * @since Jun 5, 2010
 */
public final class J4pWriteResponse extends J4pResponse<J4pWriteRequest> {

    J4pWriteResponse(J4pWriteRequest pRequest, JSONObject pJsonResponse) {
        super(pRequest, pJsonResponse);
    }
}
