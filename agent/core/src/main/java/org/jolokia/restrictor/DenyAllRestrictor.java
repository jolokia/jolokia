package org.jolokia.restrictor;

import javax.management.ObjectName;

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
    public boolean isHttpMethodAllowed(HttpMethod pMethod) {
        return false;
    }

    public boolean isTypeAllowed(String pType) {
        return false;
    }

    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return false;
    }

    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return false;
    }

    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return false;
    }

    public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
        return false;
    }
}
