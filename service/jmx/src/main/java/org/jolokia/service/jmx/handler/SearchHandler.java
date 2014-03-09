package org.jolokia.service.jmx.handler;

import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.jolokia.server.core.request.JolokiaSearchRequest;
import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;

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
 * Handler responsible for searching for MBean names.
 * @author roland
 * @since Jun 18, 2009
 */
public class SearchHandler extends AbstractCommandHandler<JolokiaSearchRequest> {

    /** {@inheritDoc} */
    public RequestType getType() {
        return RequestType.SEARCH;
    }

    /** {@inheritDoc} */
    @Override
    protected void checkForRestriction(JolokiaSearchRequest pRequest) {
        checkType();
    }

    // Previous result must be a Collection
    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("PMD.ReplaceHashtableWithMap")
    public Object doHandleAllServerRequest(MBeanServerAccess serverManager, JolokiaSearchRequest request, Object pPreviousResult)
            throws MBeanException, IOException, NotChangedException {
        checkForModifiedSince(serverManager,request);
        Set<ObjectName> names = serverManager.queryNames(request.getObjectName());

        Collection ret = pPreviousResult != null ? (Collection) pPreviousResult : new ArrayList<String>();
        for (ObjectName name : names) {
            String oName = request.getOrderedObjectName(name);
            ret.add(realm != null ? realm + "@" + oName : oName);
        }
        return ret;
    }

    /** {@inheritDoc} */
    @Override
    public boolean handleAllServersAtOnce(JolokiaSearchRequest pRequest) {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected Object doHandleSingleServerRequest(MBeanServerConnection server, JolokiaSearchRequest request) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        throw new UnsupportedOperationException("Internal: Method must not be called when all MBeanServers are handled at once");
    }
}
