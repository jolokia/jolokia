package org.jolokia.client.exception;

/*
 * Copyright 2009-2013 Roland Huss
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

/**
 * Base exception potentially raised when communicating with the server
 * @author roland
 * @since Jun 8, 2010
 */
public class J4pException extends Exception {

    /**
     * Constructor with a simple message
     *
     * @param message exception description
     */
    public J4pException(String message) {
        super(message);
    }

    /**
     * Exception with a nested exception
     *
     * @param message description of this exception
     * @param cause exception causing this exception
     */
    public J4pException(String message, Throwable cause) {
        super(message, cause);
    }

}
