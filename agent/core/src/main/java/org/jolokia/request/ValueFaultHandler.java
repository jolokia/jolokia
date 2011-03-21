/*
 * Copyright 2011 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.request;

/**
 *  Interface in order to deal with value exception
 *
 * @author roland
 * @since 15.03.11
 */

public interface ValueFaultHandler {
    /**
     * Handle the given exception and return an object
     * which can be used as a replacement for the real
     * value
     *
     * @param exception exception to ignore
     * @return replacement value or the exception is rethrown if this handler doesnt handle this exception
     * @throws T if the handler doesnt handel the exception
     */
    <T extends Throwable> Object handleException(T exception) throws T;
}
