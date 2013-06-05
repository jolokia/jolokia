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

import java.util.ArrayList;
import java.util.List;

import org.jolokia.client.request.J4pResponse;

/**
 * Exception thrown when a bulk request fails on the remote side
 * @author roland
 * @since Jun 9, 2010
 */
public class J4pBulkRemoteException extends J4pException {

    // List of results obtained from the remote side. This can be either exceptions for a single
    // request or a suceeded request
    private List results;

    /**
     * Constructor
     *
     * @param pResults list of results which should be of type {@link J4pResponse}
     */
    public J4pBulkRemoteException(List pResults) {
        super("Bulk request failed remotely");
        results = pResults;
    }

    /**
     * Get the result list. Object in this list are either {@link J4pRemoteException} for an error or
     * {@link J4pResponse} for successful requests.
     *
     * @return a list of results
     */
    public List getResults() {
        return results;
    }

    /**
     * Get the a list of responses for successful requests.
     *
     * @param <T> response type
     * @return list of successful responses.
     */
    public <T extends J4pResponse> List<T> getResponses() {
        List<T> ret = new ArrayList<T>();
        for (Object entry : results) {
            if (entry instanceof J4pResponse) {
                ret.add((T) entry);
            }
        }
        return ret;
    }

    /**
     * Get the list of {@link J4pRemoteException}. At list with all remote exceptions is collected in this list.
     *
     * @return list of remote exceptions
     */
    public List<J4pRemoteException> getRemoteExceptions() {
        List<J4pRemoteException> ret = new ArrayList<J4pRemoteException>();
        for (Object entry : results) {
            if (entry instanceof J4pRemoteException) {
                ret.add((J4pRemoteException) entry);
            }
        }
        return ret;
    }
}
