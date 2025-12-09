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
package org.jolokia.core.service.serializer;

import javax.management.AttributeNotFoundException;

/**
 * A fault handler used during wildcard path application if it detects that a given attribute is not
 * available. Used by collection/bean extractors to skip attribute which do not apply to paths.
 */
class PathAttributeFilterValueFaultHandler implements org.jolokia.core.service.serializer.ValueFaultHandler {

    private final org.jolokia.core.service.serializer.ValueFaultHandler origHandler;

    /**
     * Create a wrapping fault handler which dispatches on {@link AttributeNotFoundException} exceptions.
     * @param pOrigHandler the original handler to dispatch to
     */
    PathAttributeFilterValueFaultHandler(org.jolokia.core.service.serializer.ValueFaultHandler pOrigHandler) {
        origHandler = pOrigHandler;
    }

    /** {@inheritDoc} */
    public <T extends Throwable> Object handleException(T exception) throws T {
        if (exception instanceof AttributeNotFoundException) {
            throw new AttributeFilteredException(exception.getMessage());
        } else {
            return origHandler.handleException(exception);
        }
    }

}
