/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.client.spi;

import org.jolokia.client.JolokiaClientBuilder;

/**
 * Authenticator which is called during construction and which can perform
 * a different login than Basic-Authentication against the plain JolokiaUrl
 *
 * @author roland
 * @since 23.05.14
 */
public interface J4pClientCustomizer<T> {

//    /**
//     * Hook called before the HTTP client has been build in order to prepare
//     * for authentication
//     *
//     * @param pBuilder  the HTTP client builder
//     * @param pUser     user to authenticate
//     * @param pPassword her password
//     */
//    void authenticate(HttpClientBuilder pBuilder,String pUser, String pPassword);

    void configure(JolokiaClientBuilder builder);

//    public interface
}
