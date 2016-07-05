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

/**
 * Authenticator that succeeds if all of the Authenticators it delegates to succeed.
 *
 * @author roland
 * @since 26.05.14
 */
public class AllAuthenticator extends Authenticator {

    final private ArrayList<Authenticator> authenticators;

    public AllAuthenticator(ArrayList<Authenticator> authenticators) {
        if( authenticators == null ) {
            throw new IllegalArgumentException("authenticators cannot be null");
        }
        if( authenticators.isEmpty() ) {
            throw new IllegalArgumentException("authenticators cannot be empty");
        }
        this.authenticators = new ArrayList<Authenticator>(authenticators);
    }

    /**
     *
     * @param httpExchange
     * @return the Failure result of the first authenticator that does not succeed, or the last success result.
     */
    @Override
    public Result authenticate(HttpExchange httpExchange) {
        Result result = null;
        for (Authenticator a : authenticators) {
            result = a.authenticate(httpExchange);
            if( !(result instanceof Success) ) {
                return result;
            }
        }
        return result;
    }
}
