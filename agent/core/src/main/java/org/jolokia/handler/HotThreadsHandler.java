package org.jolokia.handler;

import org.jolokia.Version;
import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.detector.ServerHandle;
import org.jolokia.request.JmxHotThreadsRequest;
import org.jolokia.request.JmxVersionRequest;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.HotThreads;
import org.jolokia.util.RequestType;
import org.json.simple.JSONObject;

import javax.management.*;
import java.io.IOException;

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


public class HotThreadsHandler extends JsonRequestHandler<JmxHotThreadsRequest> {

    private final Configuration config;
    private ServerHandle serverHandle;

    /**
     * Constructor
     *
     * @param pConfig configuration holding additional meta data. Might be null.
     * @param pRestrictor access restrictions
     * @param pServerHandle a server handle as obtained from a {@link org.jolokia.detector.ServerDetector}
     */
    public HotThreadsHandler(Configuration pConfig, Restrictor pRestrictor, ServerHandle pServerHandle) {
        super(pRestrictor);
        serverHandle = pServerHandle;
        config = pConfig;
    }

    /** {@inheritDoc} */
    @Override
    public RequestType getType() {
        return RequestType.HOT_THREADS;
    }

    /** {@inheritDoc} */
    @Override
    protected void checkForRestriction(JmxHotThreadsRequest pRequest) {
        checkType();
    }

    /** {@inheritDoc} */
    @Override
    public boolean handleAllServersAtOnce(JmxHotThreadsRequest pRequest) {
        return true;
    }

    /** {@inheritDoc}
     * @param serverManager
     * @param request*/
    @Override
    public Object doHandleRequest(MBeanServerExecutor serverManager, JmxHotThreadsRequest request) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        JSONObject ret = new JSONObject();

        try {
            HotThreads hotThreads = (new HotThreads()).busiestThreads(request.numberOfThreads).interval(request.interval);
            String info = hotThreads.detect();
            ret.put("hot_threads", info);
        } catch (Exception e) {
        }

        return ret;
    }


    /** {@inheritDoc} */
    @Override
    // Wont be called
    public Object doHandleRequest(MBeanServerConnection server, JmxHotThreadsRequest request) {
        throw new UnsupportedOperationException("Internal: Method must not be called when all MBeanServers are handled at once");
    }

}
