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

import java.util.ArrayList;
import java.util.List;

import org.jolokia.client.response.JolokiaResponse;

/**
 * Exception thrown when a bulk request fails on the remote side
 *
 * @author roland
 * @since Jun 9, 2010
 */
public class JolokiaBulkRemoteException extends JolokiaException {

    /**
     * List of results obtained from the remote side. Each item may be a successful {@link JolokiaResponse}
     * or a {@link JolokiaRemoteException}
     */
    private final List<Object> results;

    /**
     * Constructor
     *
     * @param pResults list of results which should be of type {@link JolokiaResponse}
     */
    public JolokiaBulkRemoteException(List<Object> pResults) {
        super("Bulk request failed remotely");
        results = pResults;
    }

    /**
     * Get the result list. Object in this list are either {@link JolokiaRemoteException} for an error or
     * a {@link JolokiaResponse} for successful requests.
     *
     * @return a list of results
     */
    public List<Object> getResults() {
        return results;
    }

    /**
     * Get the a list of responses for successful requests.
     *
     * @param <T> response type
     * @return list of successful responses.
     */
    public <T extends JolokiaResponse<?>> List<T> getResponses() {
        List<T> ret = new ArrayList<>();
        for (Object entry : results) {
            if (entry instanceof JolokiaResponse) {
                //noinspection unchecked
                ret.add((T) entry);
            }
        }
        return ret;
    }

    /**
     * Get the list of {@link JolokiaRemoteException}. A list with all remote exceptions is collected in this list.
     *
     * @return list of remote exceptions
     */
    public List<JolokiaRemoteException> getRemoteExceptions() {
        List<JolokiaRemoteException> ret = new ArrayList<>();
        for (Object entry : results) {
            if (entry instanceof JolokiaRemoteException) {
                ret.add((JolokiaRemoteException) entry);
            }
        }
        return ret;
    }

}
