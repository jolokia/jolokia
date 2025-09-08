package org.jolokia.server.core.request;

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

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.management.MalformedObjectNameException;

import org.jolokia.json.JSONObject;

/**
 * Base class for so called <em>request creators</em>, which are used for creating
 * {@link JolokiaRequest}s of a specific type. These creators are used by the {@link JolokiaRequestFactory} for
 * creating a request after its type has be determined.
 *
 * Each specific request provides a static method <code>newCreator</code> which returns a factory
 * object for requests of this type.
 *
 * @author roland
 * @since 15.09.11
 */
abstract class RequestCreator<R extends JolokiaRequest> {

    /**
     * Create a {@code GET} request from a stack of path elements after initial URL mapping (like context
     * path and servlet path). For example in Jakarta Servlet environment, the stack of URL components is taken from
     * {@link jakarta.servlet.http.HttpServletRequest#getPathInfo()}
     *
     * @param pStack parsed and split GET url
     * @param pParams optional query parameters
     * @return the created request object
     * @throws MalformedObjectNameException if an object name could not be created
     */
    abstract R create(Deque<String> pStack, ProcessingParameters pParams)
            throws MalformedObjectNameException;

    /**
     * Process a {@code POST} request
     *
     * @param requestMap JSON representation of the request
     * @param pParams optional query parameters
     * @return the created request object
     * @throws MalformedObjectNameException if an object name could not be created
     */
    abstract R create(JSONObject requestMap, ProcessingParameters pParams)
            throws MalformedObjectNameException;

    /**
     * Extract extra arguments from the remaining element on the stack.
     *
     * @param pElements stack from where to extract extra elements
     * @return the remaining elements as list (but never null).
     */
    protected List<String> prepareExtraArgs(Deque<String> pElements) {
        if (pElements == null || pElements.isEmpty()) {
            return null;
        }
        List<String> ret = new ArrayList<>();
        while (!pElements.isEmpty()) {
            String element = pElements.pop();
            ret.add("*".equals(element) ? null : element);
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
    protected String popOrNull(Deque<String> stack) {
        if (stack != null && !stack.isEmpty()) {
            return stack.pop();
        } else {
            return null;
        }
    }

}
