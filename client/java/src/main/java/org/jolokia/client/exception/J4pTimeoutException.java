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
package org.jolokia.client.exception;

import java.io.IOException;

/**
 * Exception thrown in case of a timeout
 *
 * @author roland
 * @since 15.12.10
 */
public class J4pTimeoutException extends J4pException {

    /**
     * Exception thrown when a timeout occurred
     *
     * @param pMessage          error message
     * @param pTimeoutException timeout exception - actual exception may depend on the HTTP Client implementation used
     */
    public J4pTimeoutException(String pMessage, IOException pTimeoutException) {
        super(pMessage, pTimeoutException);
    }

}
