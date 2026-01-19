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
package org.jolokia.server.core.service.request;

import org.jolokia.server.core.request.JolokiaObjectNameRequest;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.service.api.AbstractJolokiaService;

/**
 * Base class for request handlers which provides some utilities methods like deciding on
 * a request based on a configured provider.
 *
 * @author roland
 * @since 21.11.13
 */
public abstract class AbstractRequestHandler extends AbstractJolokiaService<RequestHandler> implements RequestHandler {

    // Provider of this request handler
    private final String provider;

    protected AbstractRequestHandler(String pProvider, int pOrderId) {
        super(RequestHandler.class, pOrderId);
        this.provider = pProvider;
    }

    @Override
    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        return !pJolokiaRequest.isExclusive() || checkProvider(pJolokiaRequest);
    }

    /**
     * Check whether the given request match the provider for which this handler is responsible
     *
     * @param pRequest request to check
     * @return true if this handler can handle this.
     */
    protected boolean checkProvider(JolokiaRequest pRequest) {
        return pRequest instanceof JolokiaObjectNameRequest req && provider.equals(req.getProvider());
    }

    @Override
    public String getProvider() {
        return provider;
    }

    /**
     * Default implementation doesn't return any extra information, but {@code null}.
     *
     * @return extra runtime information to add for a version request
     */
    @Override
    public Object getRuntimeInfo() {
        return null;
    }

}
