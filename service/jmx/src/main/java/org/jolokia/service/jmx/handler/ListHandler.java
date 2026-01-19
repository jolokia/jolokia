/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.service.jmx.handler;

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;
import javax.management.JMException;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.core.util.EscapeUtil;
import org.jolokia.json.JSONObject;
import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.request.JolokiaListRequest;
import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.util.ProviderUtil;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;

/**
 * Handler for obtaining a list of all available MBeans and its attributes and operations (to get JSON
 * representations of {@link MBeanInfo}).
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class ListHandler extends AbstractCommandHandler<JolokiaListRequest> {

    @Override
    public RequestType getType() {
        return RequestType.LIST;
    }

    @Override
    protected void checkForRestriction(JolokiaListRequest pRequest) {
        checkType();
    }

    /**
     * Return true since a list handler needs to merge all information from all available servers and we
     * want to do it manually
     *
     * @return always true
     */
    @Override
    public boolean handleAllServersAtOnce(JolokiaListRequest pRequest) {
        return true;
    }

    @Override
    public Object doHandleSingleServerRequest(MBeanServerConnection server, JolokiaListRequest request) {
        // because we returned true in handleAllServersAtOnce()
        throw new UnsupportedOperationException("Internal: Method must not be called when all MBeanServers are handled at once");
    }

    @Override
    public Object doHandleAllServerRequest(MBeanServerAccess pServerManager, JolokiaListRequest pRequest, Object pPreviousResult)
            throws IOException, JMException, BadRequestException, NotChangedException {
        // Throw an exception if list has not changed
        checkForModifiedSince(pServerManager, pRequest);

        Deque<String> originalPathStack = EscapeUtil.reversePath(pRequest.getPathParts());

        try {
            Deque<String> pathStack = new LinkedList<>(originalPathStack);
            ObjectName oName = objectNameFromPath(pathStack);

            if (oName != null) {
                if (ProviderUtil.matchesProvider(pProvider, oName)) {
                    oName = ProviderUtil.extractProvider(oName).getObjectName();
                } else {
                    // pPreviousResult must be a Map according to the "list" data format specification
                    // the name doesn't match our "provider", so we have nothing to add - return previous result
                    return pPreviousResult != null ? pPreviousResult : new JSONObject();
                }
            }

            // this action is the full implementation of Jolokia LIST operation
            ListMBeanEachAction action = new ListMBeanEachAction(pRequest, pathStack, pProvider, context);

            if (oName == null || oName.isPattern()) {
                pServerManager.each(oName, action);
            } else {
                pServerManager.call(oName, action);
            }

            return action.getResult((JSONObject) pPreviousResult);
        } catch (MalformedObjectNameException e) {
            throw new BadRequestException("Invalid path within the MBean part given. (Path: " + pRequest.getPath() + ")", e);
        }
    }

    /**
     * Prepare an {@link ObjectName} pattern from a path (or "null" if no path is given)
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
            // revert behavior implemented for read requests in https://github.com/jolokia/jolokia/issues/106
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
