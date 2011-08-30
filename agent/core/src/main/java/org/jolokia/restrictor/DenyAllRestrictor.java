package org.jolokia.restrictor;

import javax.management.ObjectName;

import org.jolokia.util.HttpMethod;
import org.jolokia.util.RequestType;

/*
 *  Copyright 2009-2010 Roland Huss
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


/**
 * A restrictor which denies every access.
 *
 * @author roland
 * @since Jul 28, 2009
 */
public class DenyAllRestrictor implements Restrictor {

    /** {@inheritDoc} */
    public boolean isHttpMethodAllowed(HttpMethod pMethod) {
        return false;
    }

    /** {@inheritDoc} */
    public boolean isTypeAllowed(RequestType pType) {
        return false;
    }

    /** {@inheritDoc} */
    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return false;
    }

    /** {@inheritDoc} */
    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return false;
    }

    /** {@inheritDoc} */
    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return false;
    }

    /** {@inheritDoc} */
    public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
        return false;
    }
}
