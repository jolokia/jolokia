package org.jolokia.restrictor;

import javax.management.ObjectName;

import org.jolokia.util.HttpMethod;
import org.jolokia.util.RequestType;

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
 * A Restrictor is used to restrict the access to MBeans based on
 * various parameters.
 *
 * @author roland
 * @since Jul 28, 2009
 */
public interface Restrictor {

    /**
     * Check whether the HTTP method with which the request
     * was sent is allowed.
     *
     * @param pMethod method to check
     * @return true if there is no restriction on the method with which the request
     *         was sent, false otherwise
     */
    boolean isHttpMethodAllowed(HttpMethod pMethod);

    /**
     * Check whether the provided command type is allowed in principal
     *
     * @param pType type to check
     * @return true, if the type is allowed, false otherwise
     */
    boolean isTypeAllowed(RequestType pType);

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

    /**
     * Check whether cross browser access via CORS is allowed. See the
     * <a href="https://developer.mozilla.org/en/http_access_control">CORS</a> specification
     * for details
     *
     * @param pOrigin the "Origin:" URL provided within the request
     * @return true if this cross browser request allowed, false otherwise
     */
    boolean isCorsAccessAllowed(String pOrigin);
}
