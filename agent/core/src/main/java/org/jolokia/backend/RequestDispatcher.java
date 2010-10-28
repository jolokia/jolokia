package org.jolokia.backend;

import java.io.IOException;
import java.util.List;

import javax.management.*;

import org.jolokia.JmxRequest;
import org.json.simple.JSONObject;

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


/**
 * Interface for dispatching a request to a certain backend.
 *
 * @author roland
 * @since Nov 11, 2009
 */
public interface RequestDispatcher {
    /**
     * Dispatch a {@link org.jolokia.JmxRequest} to a certain backend
     * and return the result of the JMX action. This return value should be 
     *
     * @param pJmxReq the request to dispatch
     * @return result object
     * @throws InstanceNotFoundException when a certain MBean could not be found
     * @throws AttributeNotFoundException in case an attributes couldnt be resolved
     * @throws ReflectionException
     * @throws MBeanException
     * @throws IOException
     */
    JSONObject dispatchRequest(JmxRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException;


    /**
     * Method called for bulk requests, but only if this handler supports
     * bulk requests. Otherwise {@link #dispatchRequest(org.jolokia.JmxRequest)} is
     * called multiple times, for each request in the bulk request once.
     *
     * @param pJmxRequests request to issue
     * @return a list of {@link org.json.simple.JSONObject} representing the individual
     * answers.
     *
     * @throws InstanceNotFoundException
     * @throws AttributeNotFoundException
     * @throws ReflectionException
     * @throws MBeanException
     * @throws IOException
     */
    List<JSONObject> dispatchRequests(List<JmxRequest> pJmxRequests)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException;

    /**
     * Check wether current dispatcher can handle the given request
     *
     * @param pJmxRequest request to check
     * @return true if this dispatcher can handle the request
     */
    boolean canHandle(JmxRequest pJmxRequest);

    /**
     * Return true if this dispatched supports bulk requests. If this is true,
     * and a bulk request arrives, it gets all request which it can handle
     * at once so that the request can be optimized
     *
     * @return true if the dispatch supports bulk requests
     */
    boolean supportsBulkRequests();

}
