/*
 * Copyright 2014 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jolokia.client.response;

import org.jolokia.client.exception.J4pRemoteException;
import org.jolokia.client.request.JolokiaRequest;
import org.jolokia.json.JSONObject;

/**
 * An extractor can be used to add special behaviour to the extraction process before a {@link JolokiaResponse} is created.
 * This is especially useful for advanced error handling e.g. for bulk requests.
 *
 * @author roland
 * @since 23/12/14
 */
public interface J4pResponseExtractor {

    /**
     * Prepare and create {@link JolokiaResponse} based on JSON response from remote Jolokia Agent and the original
     * {@link JolokiaRequest}
     *
     * @param request        the original request
     * @param jsonResp       the JSON response as obtained from the agent
     * @param includeRequest whether the response should include (in {@link JolokiaResponse#getRequest()})
     *                       its request
     * @param <RESP>         response type
     * @param <REQ>          request type
     * @return the created {@link JolokiaResponse response}
     * @throws J4pRemoteException if the response is in an error state this exception should be thrown.
     */
    <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest> RESP extract(REQ request, JSONObject jsonResp,
                                                                                 boolean includeRequest) throws J4pRemoteException;

}
