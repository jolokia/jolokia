package org.jolokia.jmx;

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

import java.lang.annotation.*;

/**
 * Annotation for marking an MBean as a "JsonMBean", which instead of exporting
 * complex data structure JSON strings. So any non-trivial argument and return value
 * gets parsed from/translated into a JSON string.
 *
 * @author roland
 * @since 13.01.13
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface JsonMBean {
    /**
     * How deep to serialize the return value when exposing at the MBeanServer. By default,
     * no restriction applies
     * @return maximum depth used for downstram serialization
     */
    int maxDepth() default 0;

    /**
     * Maximum size of collections returned during serialization.
     * If larger, the collection is truncated. By default no truncation applies
     * @return maximum size for collections
     */
    int maxCollectionSize() default 0;

    /**
     * Maximum number of objects returned by serialization. By default no truncation
     * applies.
     * @return maximum number of objects to return
     */
    int maxObjects() default 0;

    /**
     * How to deal with exceptions occuring during deserialization. By default, exceptions
     * are thrown through (and encapsulated in a IllegalArgumentException),
     * @return
     */
    FaultHandler faultHandling() default FaultHandler.THROW_EXCEPTIONS;

    /**
     * Error handling during extraction of values
     */
    enum FaultHandler {
        /**
         * Ignore exceptions when deserializing
         */
        IGNORE_ERRORS,
        /**
         * Throw exceptions
         */
        THROW_EXCEPTIONS
    }
}
