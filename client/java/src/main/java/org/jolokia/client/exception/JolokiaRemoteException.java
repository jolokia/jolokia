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
package org.jolokia.client.exception;

import org.jolokia.client.request.JolokiaRequest;
import org.jolokia.json.JSONObject;

/**
 * Exception that occurred on the remote side (i.e the server) and which contains details about the error occurred
 * at remote Jolokia Agent side. Even errors parsed from the response, but at the client side do not
 * end with this exception. This exception should be used <em>only</em> when proper JSON error data is parsed
 * from the HTTP response body.
 *
 * @author roland
 * @since Jun 9, 2010
 */
public class JolokiaRemoteException extends JolokiaException {

    // Status code of the error, extracted from the JSON response - not from HTTP response
    private final int status;

    // Stacktrace of a remote exception (optional)
    private final String remoteStacktrace;

    // Request leading to this error
    private final JolokiaRequest request;

    // Java class of a remote error
    private final String errorType;

    // If available, this is a class name of an exception from javax.management.JMException / javax.management.JMRuntimeException
    // hierarchy
    private final String errorTypeJmx;

    // JSONObject containing value of the remote error - "error_value" field of the response JSON
    private final JSONObject errorValue;

    // the entire response JSON object
    private final JSONObject response;

    /**
     * Constructor for a remote exception, where the error details are passed as {@link JSONObject} received
     * in HTTP response. This object is expected to include error details according to Jolokia protocol.
     *
     * @param pJolokiaRequest
     * @param pJsonRespObject
     */
    public JolokiaRemoteException(JolokiaRequest pJolokiaRequest, JSONObject pJsonRespObject) {
        super(generateErrorMessage(pJolokiaRequest, pJsonRespObject));

        Object statusO = pJsonRespObject.get("status");
        status = statusO instanceof Number n ? n.intValue() : 500;

        request = pJolokiaRequest;
        response = pJsonRespObject;
        errorType = (String) pJsonRespObject.get("error_type");
        errorTypeJmx = (String) pJsonRespObject.get("error_type_jmx");
        remoteStacktrace = (String) pJsonRespObject.get("stacktrace");

        // result of org.jolokia.server.core.backend.BackendManager.convertExceptionToJson()
        // available only if "serializeException" option is true
        errorValue = (JSONObject) pJsonRespObject.get("error_value");
    }

    private static String generateErrorMessage(JolokiaRequest pJolokiaRequest, JSONObject pJsonRespObject) {
        if (pJsonRespObject.get("error") != null) {
            return "Error: " + pJsonRespObject.get("error");
        }
        Object o = pJsonRespObject.get("status");
        if (o != null) {
            if (o instanceof Number n) {
                return "Error code " + n;
            } else {
                return "Invalid status of type " + o.getClass().getName() + " ('" + o + "') received. Expected a number.";
            }
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
     * Java class of a JMX exception that may be a cause (or final) exception occurring
     * at the remote Jolokia Agent side.
     *
     * @return
     */
    public String getErrorTypeJmx() {
        return errorTypeJmx;
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
    public JolokiaRequest getRequest() {
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
