package org.jolokia.handler;

import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JmxSearchRequest;
import org.jolokia.restrictor.Restrictor;
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
 * Handler responsible for searching for MBean names.
 * @author roland
 * @since Jun 18, 2009
 */
public class SearchHandler extends JsonRequestHandler<JmxSearchRequest> {

    /**
     * Create search handler
     * 
     * @param pRestrictor access restriction to apply
     */
    public SearchHandler(Restrictor pRestrictor) {
        super(pRestrictor);
    }

    /** {@inheritDoc} */
    @Override
    public RequestType getType() {
        return RequestType.SEARCH;
    }

    /** {@inheritDoc} */
    @Override
    protected void checkForRestriction(JmxSearchRequest pRequest) {
        checkType();
    }

    /** {@inheritDoc}
     * @param serverManager
     * @param request*/
    @Override
    @SuppressWarnings("PMD.ReplaceHashtableWithMap")
    public Object doHandleRequest(MBeanServerExecutor serverManager, JmxSearchRequest request)
            throws MBeanException, IOException, NotChangedException {
        checkForModifiedSince(serverManager,request);
        Set<ObjectName> names = serverManager.queryNames(request.getObjectName());
        Set<String> ret = new LinkedHashSet<String>();

        for (ObjectName name : names) {
            ret.add(request.getOrderedObjectName(name));
        }
        return new ArrayList<String>(ret);
    }

    /** {@inheritDoc} */
    @Override
    public boolean handleAllServersAtOnce(JmxSearchRequest pRequest) {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected Object doHandleRequest(MBeanServerConnection server, JmxSearchRequest request) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        throw new UnsupportedOperationException("Internal: Method must not be called when all MBeanServers are handled at once");
    }
}
