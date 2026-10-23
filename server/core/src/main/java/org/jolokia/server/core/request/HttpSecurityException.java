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
 * Historically, Jolokia was throwing {@link SecurityException} in several places. These exceptions
 * where translated to "Jolokia JSON error response".
 * However some errors should be transmitted directly with HTTP response code 403. So when Jolokia
 * rejects the request because of {@link org.jolokia.server.core.restrictor.policy.PolicyRestrictor} violations,
 * client should not get a JSON response.
 */
public class HttpSecurityException extends RuntimeException {

    int code = 403;

    public HttpSecurityException(String message) {
        super(message);
    }

    public HttpSecurityException(String message, int code) {
        super(message);
        this.code = code;
    }

    public HttpSecurityException(String message, Throwable cause) {
        super(message, cause);
    }

    public int getCode() {
        return code;
    }

}
