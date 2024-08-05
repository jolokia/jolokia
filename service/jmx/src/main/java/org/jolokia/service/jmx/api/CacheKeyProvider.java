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
package org.jolokia.service.jmx.api;

import javax.management.ObjectInstance;

import org.jolokia.server.core.service.api.AbstractJolokiaService;
import org.jolokia.server.core.service.api.JolokiaService;

/**
 * An SPI interface used by {@link org.jolokia.service.jmx.handler.ListHandler} that can be used to optimize the
 * structure of {@code list()} operation.
 */
public abstract class CacheKeyProvider extends AbstractJolokiaService<CacheKeyProvider> implements JolokiaService<CacheKeyProvider> {

    protected CacheKeyProvider(int pOrderId) {
        super(CacheKeyProvider.class, pOrderId);
    }

    /**
     * By providing a non-null key for an {@link ObjectInstance} an extension may tell Jolokia that the JSON data
     * for {@link javax.management.MBeanInfo} can be shared with other {@link ObjectInstance instances} that use
     * the same cache key. This heavily optimizes memory usage and size of {@code list()} response.
     * @param objectInstance
     * @return
     */
    public abstract String determineKey(ObjectInstance objectInstance);

}
