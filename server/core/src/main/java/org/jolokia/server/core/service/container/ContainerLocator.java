/*
 * Copyright 2009-2024 Roland Huss
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
package org.jolokia.server.core.service.container;

import org.jolokia.server.core.service.api.JolokiaService;

/**
 * <p>A locator Jolokia service that can be registered by {@link org.jolokia.server.core.detector.ServerDetector}
 * during initialization phase.
 * This service can be used by other Jolokia services which may use container services/configuration
 * to control how Jolokia works.</p>
 *
 * <p>Original use case is Artemis server detector which finds an instance of Artemis broker to be used
 * by list optimizers that augment MBeanInfo with RBAC information.</p>
 */
public interface ContainerLocator extends JolokiaService<ContainerLocator> {

    /**
     * Returns an instance of a <em>runtime</em> or <em>container</em> specific class to be used by
     * a dedicated Jolokia service.
     *
     * @param clazz A class guard to ensure that the returned instance is of proper class
     * @return
     * @param <T>
     */
    <T> T locate(Class<T> clazz);

}
