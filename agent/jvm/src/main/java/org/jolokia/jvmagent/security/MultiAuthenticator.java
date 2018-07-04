/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.jvmagent.security;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpExchange;

import java.util.ArrayList;
import java.util.List;

/**
 * Authenticator encapsulating multiple other authenticators whose
 * results are combined to a single one, depending on the given mode
 *
 * <ul>
 *     <li>Mode.ALL : All authenticators must succeed for this authenticator to succeed</li>
 *     <li>Mode.ANY : A single sucessful authenticator is sufficient for this authenticator to succeed</li>
 * </ul>
 *
 * @author roland
 * @author nevenr
 * @since 26.05.14
 */
public class MultiAuthenticator extends Authenticator {

    final private ArrayList<Authenticator> authenticators;

    final private Mode mode;

    /**
     * How to combine multiple authenticators
     */
    public enum Mode {
        // All authenticators must match
        ALL,
        // At least one authenticator must match
        ANY;

        public static Mode fromString(String inStr) {
            if (inStr == null || inStr.isEmpty()) {
                return ANY; // Default mode
            }
            if (inStr.equalsIgnoreCase("any")){
                return ANY;
            }
            if (inStr.equalsIgnoreCase("all")){
                return ALL;
            }
            throw new IllegalArgumentException(String.format("Unknown multi authenticator mode %s. Must be either 'any' or 'all'", inStr));
        }
    }

    public MultiAuthenticator(Mode mode, List<Authenticator> authenticators) {
        if (authenticators == null) {
            throw new IllegalArgumentException("Authenticators cannot be null");
        }
        if (authenticators.isEmpty()) {
            throw new IllegalArgumentException("Authenticators cannot be empty");
        }
        this.authenticators = new ArrayList<Authenticator>(authenticators);
        this.mode = mode;
    }

    /**
     * Authenticate against the given request
     *
     * @param httpExchange request and response object
     * @return the result of the first authenticator that does succeed, or the last failure result.
     */
    @Override
    public Result authenticate(HttpExchange httpExchange) {
        Result result = null;
        for (Authenticator a : authenticators) {
            result = a.authenticate(httpExchange);
            if ((result instanceof Success && mode == Mode.ANY) ||
                (!(result instanceof Success) && mode == Mode.ALL)) {
                return result;
            }
        }
        // Return last result, which is either SUCCESS for mode.ALL or FAILURE for mode.ANY
        return result;
    }
}
