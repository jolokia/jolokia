package org.jolokia.service.jmx.handler;


import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.JolokiaListRequest;
import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.util.*;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.json.JSONObject;

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
 * Handler for obtaining a list of all available MBeans and its attributes
 * and operations.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class ListHandler extends AbstractCommandHandler<JolokiaListRequest> {

    /** {@inheritDoc} */
    public RequestType getType() {
        return RequestType.LIST;
    }

    /**
     * Return true since a list handler needs to merge all information
     *
     * @return always true
     */
    @Override
    public boolean handleAllServersAtOnce(JolokiaListRequest pRequest) {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected void checkForRestriction(JolokiaListRequest pRequest) {
        checkType();
    }

    // pPreviousResult must be a Map according to the "list" data format specification
    /** {@inheritDoc} */
    @Override
    public Object doHandleAllServerRequest(MBeanServerAccess pServerManager, JolokiaListRequest pRequest, Object pPreviousResult)
            throws IOException, NotChangedException {
        // Throw an exception if list has not changed
        checkForModifiedSince(pServerManager, pRequest);

        Deque<String> originalPathStack = org.jolokia.core.util.EscapeUtil.reversePath(pRequest.getPathParts());
        int maxDepth = pRequest.getParameterAsInt(ConfigKey.MAX_DEPTH);
        boolean useCanonicalName = pRequest.getParameterAsBool(ConfigKey.CANONICAL_NAMING);
        boolean listKeys = pRequest.getParameterAsBool(ConfigKey.LIST_KEYS);
        boolean listCache = pRequest.getParameterAsBool(ConfigKey.LIST_CACHE);

        ObjectName oName = null;
        try {
            Deque<String> pathStack = new LinkedList<>(originalPathStack);
            oName = objectNameFromPath(pathStack);

            if (oName != null) {
                if (ProviderUtil.matchesProvider(pProvider, oName)) {
                    oName = ProviderUtil.extractProvider(oName).getObjectName();
                } else {
                    return pPreviousResult != null ? pPreviousResult : new JSONObject();
                }
            }

            ListMBeanEachAction action = new ListMBeanEachAction(maxDepth, pathStack, useCanonicalName, listKeys, listCache, pProvider, context);
            return executeListAction(pServerManager, (Map<?, ?>) pPreviousResult, oName, action);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid path within the MBean part given. (Path: " + pRequest.getPath() + ")",e);
        } catch (InstanceNotFoundException e) {
            throw new IllegalArgumentException("No MBean '" + oName + "' found",e);
        } catch (JMException e) {
            throw new IllegalStateException("Internal error while retrieving list: " + e, e);
        }
    }

    private Object executeListAction(MBeanServerAccess pServerManager, Map<?, ?> pPreviousResult, ObjectName pName, ListMBeanEachAction pAction)
            throws IOException, ReflectionException, MBeanException, AttributeNotFoundException, InstanceNotFoundException {
        if (pName == null || pName.isPattern()) {
            pServerManager.each(pName, pAction);
        } else {
            pServerManager.call(pName, pAction);
        }
        return pAction.getResult(pPreviousResult);
    }

    /** {@inheritDoc} */
    @Override
    public Object doHandleSingleServerRequest(MBeanServerConnection server, JolokiaListRequest request) {
        throw new UnsupportedOperationException("Internal: Method must not be called when all MBeanServers are handled at once");
    }

    // ==========================================================================================================

    /**
     * Prepare an objectname pattern from a path (or "null" if no path is given)
     * @param pPathStack path
     * @return created object name (either plain or a pattern)
     */
    private ObjectName objectNameFromPath(Deque<String> pPathStack) throws MalformedObjectNameException {
        if (pPathStack.isEmpty()) {
            return null;
        }
        Deque<String> path = new LinkedList<>(pPathStack);
        String domain = path.pop();
        if (domain == null) {
            // revert behaviour implemented for read requests in https://github.com/jolokia/jolokia/issues/106
            domain = "*";
        }
        if (path.isEmpty()) {
            return new ObjectName(domain + ":*");
        }
        String props = path.pop();
        if (props == null) {
            props = "*";
        }
        return new ObjectName(domain + ":" + props);
    }

}
