package org.jolokia.client.exception;

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

import org.jolokia.client.request.J4pRequest;
import org.json.simple.JSONObject;

/**
 * Exception occurred on the remote side (i.e the server).
 *
 * @author roland
 * @since Jun 9, 2010
 */
public class J4pRemoteException extends J4pException {

    // Status code of the error
    private int status;

    // Stacktrace of a remote exception (optional)
    private String remoteStacktrace;

    // Request leading to this error
    private J4pRequest request;

    // Java class of remote error
    private String errorType;
    
    // JSONObject containing value of the remote error
    private JSONObject errorValue;

    // String containing the entire response
    private JSONObject response;

    /**
     * Constructor for a remote exception
     *
     * @param pMessage error message of the exception occurred remotely
     * @param pErrorType kind of error used
     * @param pStatus status code
     * @param pStacktrace stacktrace of the remote exception
     */
    public J4pRemoteException(J4pRequest pJ4pRequest, String pMessage, String pErrorType, int pStatus, String pStacktrace, JSONObject pErrorValue) {
        super(pMessage);
        status = pStatus;
        errorType = pErrorType;
        remoteStacktrace = pStacktrace;
        request = pJ4pRequest;
        errorValue = pErrorValue;
    }

    public J4pRemoteException(J4pRequest pJ4pRequest, JSONObject pJsonRespObject) {
        super(generateErrorMessage(pJ4pRequest, pJsonRespObject));
	Object statusO = pJsonRespObject.get("status");
        Long statusL = statusO instanceof Long ? (Long) statusO : null;
        status = statusL != null ? statusL.intValue() : 500;
        request = pJ4pRequest;
	response = pJsonRespObject;
        errorType = (String) pJsonRespObject.get("error_type");
        remoteStacktrace = (String) pJsonRespObject.get("stacktrace");
        errorValue = (JSONObject) pJsonRespObject.get("error_value");
    }

    private static String generateErrorMessage(J4pRequest pJ4pRequest, JSONObject pJsonRespObject) {
	if( pJsonRespObject.get("error") != null ) {
		return "Error: " + (String) pJsonRespObject.get("error");
	}
	Object o = pJsonRespObject.get("status");
	if( o != null && !(o instanceof Long)) {
		return "Invalid status of type " + o.getClass().getName() + "('" + o.toString() + "') received";
	}
	return "Invalid response received";
    }

    /**
     * Java class of remote exception in string representation
     *
     * @return error type
     */
    public String getErrorType() {
        return errorType;
    }

    /**
     * Get status of this response (similar in meaning of HTTP status)
     *
     * @return status
     */
    public int getStatus() {
        return status;
    }

    /**
     * Get the server side stack trace as string. Return <code>null</code>
     * if no stack trace could be retrieved.
     *
     * @return server side stack trace as string
     */
    public String getRemoteStackTrace() {
        return remoteStacktrace;
    }

    /**
     * Get the request leading to this exception. Can be null if this exception occurred during a bulk requests
     * containing multiple single requests.
     *
     * @return request which caused this exception
     */
    public J4pRequest getRequest() {
        return request;
    }

    /**
     * Get value of the remote error.
     * 
     * @return value of the remote error as JSON
     */
    public JSONObject getErrorValue() {
        return errorValue;
    }


    /**
     * Get the response string, or null if unavailable
     *
     * @return value of the json response string
     */
    public JSONObject getResponse() {
        return response;
    }
}
