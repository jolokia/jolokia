package org.jolokia.config;

import org.jolokia.JmxRequest;

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
 * @author roland
 * @since Jul 28, 2009
 */
public interface Restrictor {

    /**
     * Check whether the provided command type is allowed in principal
     *
     * @param pType type to check
     * @return true, if the type is allowed, false otherwise
     */
    boolean isTypeAllowed(JmxRequest.Type pType);

    /**
     * Check whether reading of an attribute is allowed
     *
     * @param pName MBean name
     * @param pAttribute attribute to check
     * @return true if access is allowed
     */
    boolean isAttributeReadAllowed(ObjectName pName,String pAttribute);

    /**
     * Check whether writing of an attribute is allowed
     *
     * @param pName MBean name
     * @param pAttribute attribute to check
     * @return true if access is allowed
     */
    boolean isAttributeWriteAllowed(ObjectName pName,String pAttribute);

    /**
     * Check whether execution of an operation is allowed
     *
     * @param pName MBean name
     * @param pOperation attribute to check
     * @return true if access is allowed
     */
    boolean isOperationAllowed(ObjectName pName,String pOperation);

    /**
     * Check whether access from the connected client is allowed. If at least
     * one of the given parameters matches, then this method returns true.
     *
     * @return true is access is allowed
     * @param pHostOrAddress one or more host or address names
     */
    boolean isRemoteAccessAllowed(String ... pHostOrAddress);
}
