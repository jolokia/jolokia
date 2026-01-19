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
package org.jolokia.server.core.request;

/**
 * An exception that is used to indicate a processing error, which should result in HTTP 400 error
 * ({@link jakarta.servlet.http.HttpServletResponse#SC_BAD_REQUEST}) and empty body.
 * This exception indicates something bad happened before we had a chance to analyze the incoming JSON
 * request (or when parsing the incoming JSON request). Also, this exception is always the sender's fault.
 */
public class BadRequestException extends Exception {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }

}
