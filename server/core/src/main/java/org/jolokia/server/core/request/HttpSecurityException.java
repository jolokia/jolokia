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
 * <p>A security exception related to HTTP protocol and network restrictions. Used for rejecting responses
 * not related to JMX access policy (operation, MBean access, ...) but to a policy for client IP/hostname,
 * Origin violation or restriction on HTTP method used.</p>
 *
 * <p>Historically, Jolokia was throwing a {@link SecurityException} when rejecting JMX access to a particular
 * MBean or when rejecting entire request due to policy violation. These exceptions
 * where translated to "Jolokia JSON error response" delivered as "error JSON", but the HTTP error was still 200.</p>
 *
 * <p>Some errors could be transmitted directly with HTTP response code 403 (or 405). For compatibility reasons
 * we'll still be returning these "error JSON" responses with HTTP 200, but this dedicated exception class may be
 * used to distinguish JMX rejections and HTTP rejections.</p>
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
