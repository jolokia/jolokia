package org.jolokia.osgi.restrictor;

import javax.management.ObjectName;

import org.jolokia.restrictor.AllowAllRestrictor;

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


/**
 * Sample restrictor, which grants read/write/exec access only
 * on a certain JMX-domain.
 *
 * @author roland
 * @since 22.03.11
 */
public class SampleRestrictor extends AllowAllRestrictor {

    private String allowedDomain;

    public SampleRestrictor(String pAllowedDomain) {
        allowedDomain = pAllowedDomain;
    }

    @Override
    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return checkObjectName(pName);
    }

    @Override
    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return checkObjectName(pName);
    }

    @Override
    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return checkObjectName(pName);
    }

    private boolean checkObjectName(ObjectName pName) {
        return pName.getDomain().equals(allowedDomain);
    }
}
