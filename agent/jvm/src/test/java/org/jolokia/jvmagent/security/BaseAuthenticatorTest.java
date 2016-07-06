package org.jolokia.jvmagent.security;/*
 * 
 * Copyright 2014 Roland Huss
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

import java.util.Arrays;

import javax.security.auth.Subject;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.jolokia.config.ConfigKey;

import static org.easymock.EasyMock.*;

/**
 * @author roland
 * @since 27/05/15
 */
public class BaseAuthenticatorTest {
    protected HttpExchange createHttpExchange(Headers respHeaders, String... reqHeaderValues) {
        return createHttpExchange(respHeaders, null, reqHeaderValues);
    }

    protected HttpExchange createHttpExchange(Headers respHeaders, Subject subject, String... reqHeaderValues) {
        HttpExchange ex = createMock(HttpExchange.class);
        Headers reqHeaders = new Headers();
        for (int i = 0; i < reqHeaderValues.length; i+=2) {
            reqHeaders.put(reqHeaderValues[i], Arrays.asList(reqHeaderValues[i + 1]));
        }
        expect(ex.getResponseHeaders()).andStubReturn(respHeaders);
        expect(ex.getRequestHeaders()).andStubReturn(reqHeaders);
        // For JDK6:
        expect(ex.getHttpContext()).andStubReturn(null);
        if (subject != null) {
            ex.setAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE, subject);
        }
        replay(ex);
        return ex;
    }


}
