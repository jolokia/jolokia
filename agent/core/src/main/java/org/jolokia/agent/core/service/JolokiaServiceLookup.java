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

package org.jolokia.agent.core.service;

import java.util.Set;

/**
 * Interface for a service factory which is consulted every time services
 * are required. This factory can be used for dynamic services which
 * can come and go.
 *
 * @author roland
 * @since 21.04.13
 */
public interface JolokiaServiceLookup {

    /**
     * Get the current list of available services for a certain type.
     *
     * @param pType type for which to get the services
     * @return list of services for the required type or an empty set
     */
    <T extends JolokiaService> Set<T> getServices(Class<T> pType);

    /**
     * Lifecycle method called when the service managed starts up
     *
     * @param pJolokiaContext created context
     */
    void init(JolokiaContext pJolokiaContext);

    /**
     * Lifecycle method when the service manager stops
     */
     void destroy();
}
