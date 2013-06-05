package org.jolokia.request;

/*
 * Copyright 2009-2011 Roland Huss
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

import java.util.*;

import javax.management.MalformedObjectNameException;

import org.jolokia.config.ProcessingParameters;

/**
 * Base class for so called <em>request creators</em>, which are used for creating
 * {@link JmxRequest}s of a specific type. These creators are used by the {@link JmxRequestFactory} for
 * creating a request after its type has be determined.
 *
 * Each specific request provides a static method <code>newCreator</code> which returns a factory
 * object for requests of this type.
 *
 * @author roland
 * @since 15.09.11
 */
abstract class RequestCreator<R extends JmxRequest> {

    /**
     * Create a GET request.
     *
     * @param pStack parsed and splitted GET url
     * @param pParams optional query parameters
     * @return the created request object
     * @throws MalformedObjectNameException if an object name could not be created
     */
    abstract R create(Stack<String> pStack, ProcessingParameters pParams)
            throws MalformedObjectNameException;

    /**
     * Process a POST request
     *
     * @param requestMap JSON representation of the request
     * @param pParams optional query parameters
     * @return the created request object
     * @throws MalformedObjectNameException if an object name could not be created
     */
    abstract R create(Map<String, ?> requestMap, ProcessingParameters pParams)
            throws MalformedObjectNameException;


    /**
     * Extract extra arguments from the remaining element on the stack.
     *
     * @param pElements stack from where to extract extra elements
     * @return the remaining elements as list (but never null).
     */
    protected List<String> prepareExtraArgs(Stack<String> pElements) {
        if (pElements == null || pElements.size() == 0) {
            return null;
        }
        List<String> ret = new ArrayList<String>();
        while (!pElements.isEmpty()) {
            ret.add(pElements.pop());
        }
        return ret;
    }

    /**
     * Return the top level element of the the given stack or <code>null</code> if the stack
     * is empty
     *
     * @param stack stack to examine
     * @return null or top element
     */
    protected String popOrNull(Stack<String> stack) {
        if (stack != null && !stack.isEmpty()) {
            return stack.pop();
        } else {
            return null;
        }
    }

}
