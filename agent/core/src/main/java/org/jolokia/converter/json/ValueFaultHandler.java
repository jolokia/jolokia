package org.jolokia.converter.json;

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
     * @param exception exception to handle
     * @return replacement value or the exception is rethrown if this handler doesnt handle this exception
     * @throws T if the handler does not handle the exception
     */
    <T extends Throwable> Object handleException(T exception) throws T;

    /**
     * Fault handler which returns a simple string representation of the exception
     */
    ValueFaultHandler IGNORING_VALUE_FAULT_HANDLER = new ValueFaultHandler() {
        /**
         * Ignores any exeception and records them as a string which can be used for business
         *
         * @param exception exception to ignore
         * @return a descriptive string of the exception
         */
        public <T extends Throwable> Object handleException(T exception) throws T {
            return "ERROR: " + exception.getMessage() + " (" + exception.getClass() + ")";
        }
    };

    /**
     * Fault handler for simply rethrowing a given exception.
     */
    ValueFaultHandler THROWING_VALUE_FAULT_HANDLER = new ValueFaultHandler() {

        /**
         * Ret-throws the given exception
         * @param exception exception given
         * @return nothing
         * @throws T always
         */
        public <T extends Throwable> Object handleException(T exception) throws T {
            // Don't handle exception on our own, we rethrow it
            throw exception;
        }
    };

}
