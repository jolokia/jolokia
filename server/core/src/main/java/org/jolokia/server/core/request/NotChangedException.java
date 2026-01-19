/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.server.core.request;

/**
 * Exception thrown when an {@link org.jolokia.server.core.config.ConfigKey#IF_MODIFIED_SINCE} parameter was given and
 * the requested resourced hasn't change
 *
 * @author roland
 * @since 07.03.13
 */
public class NotChangedException extends Exception {

    private final JolokiaRequest request;

    /**
     * Constructor
     * @param pRequest which lead to this exception
     */
    public NotChangedException(JolokiaRequest pRequest) {
        request = pRequest;
    }

    /**
     * Request which lead to this exception
     * @return request
     */
    public JolokiaRequest getRequest() {
        return request;
    }

}
