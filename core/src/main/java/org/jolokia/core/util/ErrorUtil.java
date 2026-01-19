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
package org.jolokia.core.util;

import javax.management.JMException;
import javax.management.JMRuntimeException;

import org.jolokia.json.JSONObject;

public class ErrorUtil {

    private ErrorUtil() {
    }

    /**
     * Extract class and exception message for an error message
     *
     * @param exception
     * @return
     */
    public static String getExceptionMessage(Throwable exception) {
        String message = exception.getLocalizedMessage();
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        return exception.getClass().getName();
    }

    /**
     * When Jolokia Agent returns a JSON error message, some fields can be configured directly from
     * the exception object.
     *
     * @param result
     * @param exception
     */
    public static void addBasicErrorResponseInformation(JSONObject result, Throwable exception) {
        // Error type is a FQCN of the exception - potentially to be reconstructed at the client side
        result.put("error_type", exception.getClass().getName());
        // Error message
        result.put("error", getExceptionMessage(exception));

        while (exception != null) {
            if (exception instanceof JMException || exception instanceof JMRuntimeException) {
                result.put("error_type_jmx", exception.getClass().getName());
                break;
            }
            if (exception.getCause() == exception) {
                break;
            } else {
                exception = exception.getCause();
            }
        }
    }

}
