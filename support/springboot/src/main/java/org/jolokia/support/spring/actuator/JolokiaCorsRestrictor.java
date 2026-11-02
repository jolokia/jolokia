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
package org.jolokia.support.spring.actuator;

import javax.management.ObjectName;

import org.jolokia.server.core.restrictor.policy.CorsChecker;
import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.util.HttpMethod;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.support.spring.actuator.JolokiaProperties.CorsConfiguration;

/** Overrides CORS settings while retaining the existing restrictor for all other checks. */
final class JolokiaCorsRestrictor implements Restrictor {

    private final Restrictor delegate;
    private final CorsChecker corsChecker;

    JolokiaCorsRestrictor(CorsConfiguration config, Restrictor delegate) {
        if (config.allowOrigin().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("CORS allow-origin must not contain empty entries");
        }
        this.delegate = delegate;
        corsChecker = new CorsChecker(config.allowOrigin(), config.strictChecking(), config.ignoreScheme());
    }

    @Override
    public boolean isHttpMethodAllowed(HttpMethod method) {
        return delegate.isHttpMethodAllowed(method);
    }

    @Override
    public boolean isCorsMethodAllowed(HttpMethod method) {
        return delegate.isCorsMethodAllowed(method);
    }

    @Override
    public boolean isTypeAllowed(RequestType type) {
        return delegate.isTypeAllowed(type);
    }

    @Override
    public boolean isAttributeReadAllowed(ObjectName name, String attribute) {
        return delegate.isAttributeReadAllowed(name, attribute);
    }

    @Override
    public boolean isAttributeWriteAllowed(ObjectName name, String attribute) {
        return delegate.isAttributeWriteAllowed(name, attribute);
    }

    @Override
    public boolean isOperationAllowed(ObjectName name, String operation) {
        return delegate.isOperationAllowed(name, operation);
    }

    @Override
    public boolean isRemoteAccessAllowed(String... hostOrAddress) {
        return delegate.isRemoteAccessAllowed(hostOrAddress);
    }

    @Override
    public boolean isObjectNameHidden(ObjectName name) {
        return delegate.isObjectNameHidden(name);
    }

    @Override
    public boolean isOriginAllowed(String origin, boolean onlyWhenStrictCheckingIsEnabled) {
        return corsChecker.check(origin, onlyWhenStrictCheckingIsEnabled);
    }

    @Override
    public boolean ignoreScheme() {
        return corsChecker.ignoreScheme();
    }
}
