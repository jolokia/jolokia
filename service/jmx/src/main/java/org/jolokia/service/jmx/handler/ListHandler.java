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
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.management.JMException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.jolokia.core.util.EscapeUtil;
import org.jolokia.json.JSONObject;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.request.JolokiaListRequest;
import org.jolokia.server.core.request.JolokiaRequestFactory;
import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.request.ProcessingParameters;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.ProviderUtil;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.service.jmx.handler.list.MBeanInfoData;

/**
 * Handler for obtaining a list of all available MBeans and its attributes and operations (to get JSON
 * representations of {@link MBeanInfo}).
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class ListHandler extends AbstractCommandHandler<JolokiaListRequest> {

    /** This is how this request handler accesses managed {@link MBeanServer MBean servers}. */
    private MBeanServerAccess jmxAccess;

    /**
     * This is a cache for full JSON representation of {@link MBeanInfo} for known objects. Initially
     * it is populated with the information for {@link java.lang.management.PlatformManagedObject platform MXBeans}
     * but we can cache any object.
     */
    private final Map<ObjectName, JSONObject> cache = new ConcurrentHashMap<>();

    /**
     * Cache without {@link javax.management.openmbean.OpenType} information
     */
    private final Map<ObjectName, JSONObject> noOpenTypeCache = new ConcurrentHashMap<>();

    @Override
    public RequestType getType() {
        return RequestType.LIST;
    }

    @Override
    public void init(JolokiaContext pContext, String pProvider) {
        super.init(pContext, pProvider);

        jmxAccess = pContext.getMBeanServerAccess();
        cachePlatformMbeans();
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
            ListMBeanEachAction action = new ListMBeanEachAction(pRequest, pathStack, pProvider, context, cache, noOpenTypeCache);

            if (oName == null || oName.isPattern()) {
                // needed, because MBeanServerAccess will query for all matching MBeans and call our action
                pServerManager.each(oName, action);
            } else {
                // here there's no querying`
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

    /**
     * Cache JSON representation of JSON version of {@link MBeanInfo} for all known MBeans at initialization time.
     */
    private void cachePlatformMbeans() {
        Set<String> platformMBeans = Set.of(
            MBeanServerDelegate.DELEGATE_NAME.getCanonicalName(),
            ManagementFactory.CLASS_LOADING_MXBEAN_NAME,
            ManagementFactory.COMPILATION_MXBEAN_NAME,
            ManagementFactory.MEMORY_MXBEAN_NAME,
            ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME,
            ManagementFactory.RUNTIME_MXBEAN_NAME,
            ManagementFactory.THREAD_MXBEAN_NAME
        );
        Set<String> platformMBeanGroups = Set.of(
            ManagementFactory.MEMORY_MANAGER_MXBEAN_DOMAIN_TYPE,
            ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE,
            ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE
        );
        Set<String> optionalMBeans = Set.of(
            "jdk.management.jfr:type=FlightRecorder",
            "java.util.logging:type=Logging"
        );

        try {
            JolokiaListRequest list1 = JolokiaRequestFactory.createGetRequest("list", new ProcessingParameters(Collections.emptyMap()));
            JolokiaListRequest list2 = JolokiaRequestFactory.createGetRequest("list", new ProcessingParameters(Map.of(ConfigKey.OPEN_TYPES, "true")));

            for (JolokiaListRequest list : Arrays.asList(list1, list2)) {
                boolean withOpenTypes = list.getParameterAsBool(ConfigKey.OPEN_TYPES);
                Map<ObjectName, JSONObject> c = withOpenTypes ? cache : noOpenTypeCache;
                MBeanInfoData data = new MBeanInfoData(null, null, list, null, null);

                // known MBeans like java.lang:type=Runtime
                for (String name : platformMBeans) {
                    try {
                        jmxAccess.call(ObjectName.getInstance(name), new MBeanServerAccess.MBeanAction<>() {
                            @Override
                            public Void execute(MBeanServerConnection jmx, ObjectName objectName, Object... extraArgs) throws IOException, JMException {
                                data.addMBeanInfo(jmx, new ObjectInstance(objectName, null), Collections.emptySet(), Collections.emptySet());
                                return null;
                            }
                        });
                    } catch (IOException | JMException ignored) {
                    }
                }
                // optional MBeans which may not be available
                for (String name : optionalMBeans) {
                    try {
                        jmxAccess.call(ObjectName.getInstance(name), new MBeanServerAccess.MBeanAction<>() {
                            @Override
                            public Void execute(MBeanServerConnection jmx, ObjectName objectName, Object... extraArgs) throws IOException, JMException {
                                if (jmx.isRegistered(objectName)) {
                                    data.addMBeanInfo(jmx, new ObjectInstance(objectName, null), Collections.emptySet(), Collections.emptySet());
                                }
                                return null;
                            }
                        });
                    } catch (IOException | JMException ignored) {
                    }
                }
                // only first from a group, for example java.lang:type=MemoryPool,name=Metaspace
                for (String name : platformMBeanGroups) {
                    try {
                        for (MBeanServerConnection server : jmxAccess.getMBeanServers()) {
                            Set<ObjectName> names = server.queryNames(ObjectName.getInstance(name + ",*"), null);
                            if (names != null && !names.isEmpty()) {
                                data.addMBeanInfo(server, new ObjectInstance(names.iterator().next(), null), Collections.emptySet(), Collections.emptySet());
                                break;
                            }
                        }
                    } catch (IOException | JMException ignored) {
                    }
                }

                if (data.applyPath() instanceof JSONObject result) {
                    result.forEach((domain, domainData) -> {
                        if (domainData instanceof JSONObject mBeansData) {
                            mBeansData.forEach((keys, mBeanData) -> {
                                if (mBeanData instanceof JSONObject json) {
                                    try {
                                        ObjectName platformMBeanName = ObjectName.getInstance(domain + ":" + keys);
                                        c.put(platformMBeanName, json);
                                    } catch (MalformedObjectNameException ignored) {
                                    }
                                }
                            });
                        }
                    });
                }

                // cache remaining MBeans from the known groups based on what we've already cached
                for (String name : platformMBeanGroups) {
                    for (ObjectName cached : c.keySet()) {
                        try {
                            if (ObjectName.getInstance(name + ",*").apply(cached)) {
                                for (MBeanServerConnection server : jmxAccess.getMBeanServers()) {
                                    boolean found = false;
                                    Set<ObjectName> names = server.queryNames(ObjectName.getInstance(name + ",*"), null);
                                    for (ObjectName n : names) {
                                        if (!c.containsKey(n)) {
                                            c.put(n, c.get(cached));
                                        }
                                    }
                                }
                            }
                        } catch (IOException | MalformedObjectNameException ignored) {
                        }
                    }
                }
            }
        } catch (BadRequestException ignored) {
        }
    }

}
