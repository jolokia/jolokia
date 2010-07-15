package org.jolokia.client.request;

import java.util.Collections;
import java.util.List;

import org.json.simple.JSONObject;

/**
 * @author roland
 * @since Apr 24, 2010
 */
public class J4pVersionRequest extends J4pRequest {

    public J4pVersionRequest() {
        super(J4pType.VERSION);
    }

    @Override
    List<String> getRequestParts() {
        return Collections.emptyList();
    }

    @Override
    J4pVersionResponse createResponse(JSONObject pResponse) {
        return new J4pVersionResponse(this,pResponse);
    }

}
