package org.jolokia.restrictor;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import javax.management.ObjectName;

import org.jolokia.util.HttpMethod;
import org.jolokia.util.RequestType;

/**
 * Base restrictor which alway returns the constant given
 * at construction time
 *
 * @author roland
 * @since 06.10.11
 */
public abstract class AbstractConstantRestrictor implements Restrictor {

    private boolean isAllowed;

    /**
     * Create restrictor which always returns the given value on every check
     * method.
     *
     * @param pAllowed whether access is allowed or denied
     */
    protected AbstractConstantRestrictor(boolean pAllowed) {
        isAllowed = pAllowed;
    }

    /** {@inheritDoc} */
    public boolean isHttpMethodAllowed(HttpMethod pMethod) {
        return isAllowed;
    }

    /** {@inheritDoc} */
    public boolean isTypeAllowed(RequestType pType) {
        return isAllowed;
    }

    /** {@inheritDoc} */
    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return isAllowed;
    }

    /** {@inheritDoc} */
    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return isAllowed;
    }

    /** {@inheritDoc} */
    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return isAllowed;
    }

    /** {@inheritDoc} */
    public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
        return isAllowed;
    }

    /** {@inheritDoc} */
    public boolean isOriginAllowed(String pOrigin, boolean pOnlyWhenStrictCheckingIsEnabled) {
        return isAllowed;
    }
}
