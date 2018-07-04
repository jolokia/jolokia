package org.jolokia.jvmagent.security;
/*
 *
 * Copyright 2016 Roland Huss
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

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.testng.annotations.Test;

import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 06/07/16
 */
public class MultiAuthenticatorTest {

    private final Authenticator NEGATIVE_AUTHENTICATOR = new Authenticator() {
        public Result authenticate(HttpExchange httpExchange) {
            return new Failure(401);
        }
    };

    private final Authenticator POSITIVE_AUTHENTICATOR = new Authenticator() {
        public Result authenticate(HttpExchange httpExchange) {
            return new Success(new HttpPrincipal("",""));
        }
    };

    @Test
    public void anyPositive() throws Exception {
        MultiAuthenticator authenticator =
            new MultiAuthenticator(MultiAuthenticator.Mode.ANY,
                                   Arrays.asList(NEGATIVE_AUTHENTICATOR,
                                                 POSITIVE_AUTHENTICATOR,
                                                 NEGATIVE_AUTHENTICATOR));
        assertTrue(authenticator.authenticate(null) instanceof Authenticator.Success);
    }

    @Test
    public void anyNegative() throws Exception {
        MultiAuthenticator authenticator =
            new MultiAuthenticator(MultiAuthenticator.Mode.ANY,
                                   Arrays.asList(NEGATIVE_AUTHENTICATOR,
                                                 NEGATIVE_AUTHENTICATOR,
                                                 NEGATIVE_AUTHENTICATOR));
        assertTrue(authenticator.authenticate(null) instanceof Authenticator.Failure);
    }

    @Test
    public void allPositive() throws Exception {
        MultiAuthenticator authenticator =
            new MultiAuthenticator(MultiAuthenticator.Mode.ALL,
                                   Arrays.asList(POSITIVE_AUTHENTICATOR,
                                                 POSITIVE_AUTHENTICATOR,
                                                 POSITIVE_AUTHENTICATOR));
        assertTrue(authenticator.authenticate(null) instanceof Authenticator.Success);
    }

    @Test
    public void allNegative() throws Exception {
        MultiAuthenticator authenticator =
            new MultiAuthenticator(MultiAuthenticator.Mode.ALL,
                                   Arrays.asList(NEGATIVE_AUTHENTICATOR,
                                                 POSITIVE_AUTHENTICATOR,
                                                 NEGATIVE_AUTHENTICATOR));
        assertTrue(authenticator.authenticate(null) instanceof Authenticator.Failure);
    }

    @Test
    public void multiAuthenticatorModeFromString(){
        for (String any : new String[] {"any", "ANY", "Any", "aNy"}) {
            assertSame(MultiAuthenticator.Mode.fromString(any), MultiAuthenticator.Mode.ANY);
        }
        for (String all : new String[] {"all", "ALL", "All", "aLl"}) {
            assertSame(MultiAuthenticator.Mode.fromString(all), MultiAuthenticator.Mode.ALL);
        }
    }

    @Test
    public void multiAuthenticatorModeFromStringNull() {
        assertSame(MultiAuthenticator.Mode.fromString(null), MultiAuthenticator.Mode.ANY);
    }

    @Test
    public void multiAuthenticatorModeFromStringEmpty() {
        assertSame(MultiAuthenticator.Mode.fromString(""), MultiAuthenticator.Mode.ANY);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void multiAuthenticatorModeFromStringUnknown() {
        MultiAuthenticator.Mode.fromString("something unknown !@#$%^");
    }

}
