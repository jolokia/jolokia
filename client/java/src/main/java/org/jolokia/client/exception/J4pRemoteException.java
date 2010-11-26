package org.jolokia.client.exception;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import org.jolokia.client.request.J4pRequest;

/**
 * Exception occured on the remote side (i.e the server).
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

    /**
     * Constructor for a remote exception
     *
     * @param pMessage error message of the exception occurred remotely
     * @param pStatus status code
     * @param pStacktrace stacktrace of the remote exception
     */
    public J4pRemoteException(J4pRequest pJ4pRequest,String pMessage,int pStatus,String pStacktrace) {
        super(pMessage);
        status = pStatus;
        remoteStacktrace = pStacktrace;
        request = pJ4pRequest;
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
}
