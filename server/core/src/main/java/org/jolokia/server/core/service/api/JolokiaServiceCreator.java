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

package org.jolokia.server.core.service.api;

import java.util.Set;


/**
 * Object for creating static services
 *
 * @author roland
 * @since 14.06.13
 */
public interface JolokiaServiceCreator {

    /**
     * Get the services created by this creator. The service created can
     * be of various types
     *
     * @return created services
     */
    Set<JolokiaService<?>> getServices(org.jolokia.core.api.LogHandler logHandler);
}
