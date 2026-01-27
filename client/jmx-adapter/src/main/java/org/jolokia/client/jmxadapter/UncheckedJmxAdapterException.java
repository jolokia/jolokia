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
package org.jolokia.client.jmxadapter;

import java.io.IOException;
import javax.management.JMRuntimeException;

/**
 * Exception class to make exception handling in {@link javax.management.MBeanServerConnection} implementation
 * easier. The cause should never be an {@link java.io.IOException}.
 */
public class UncheckedJmxAdapterException extends RuntimeException {

    public UncheckedJmxAdapterException(Exception e) {
        super(e);
        if (e instanceof IOException) {
            throw new IllegalArgumentException("UncheckedJmxAdapterException should not wrap IOExceptions");
        }
    }

    /**
     * Throws the {@link #getCause()} wrapped in {@link javax.management.JMRuntimeException}
     */
    public void throwGenericJMRuntimeCause() throws JMRuntimeException {
        Throwable cause = getCause();
        JMRuntimeException ex = new JMRuntimeException(cause.getMessage());
        ex.initCause(cause);
        throw ex;
    }

}
