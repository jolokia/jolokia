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

package org.jolokia.service.impl;

import java.util.List;

import org.jolokia.service.JolokiaService;

/**
 * @author roland
 * @since 28.03.13
 */
public class JolokiaServiceManager {

    protected LocalServiceFactory localServiceFactory;

    /**
     * Get all services of a certain type currently registered
     *
     * @param <T> service type to fetch
     * @return list of services detected or an empty list
     */
    <T extends JolokiaService> List<T> getServices(Class<T> pServiceType) {
        return null;
    }
}
