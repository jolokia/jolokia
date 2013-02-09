package org.jolokia.handler;

import java.io.IOException;

import javax.management.*;

import org.jolokia.Version;
import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.detector.ServerHandle;
import org.jolokia.request.JmxVersionRequest;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.RequestType;
import org.json.simple.JSONObject;

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
 * Get the version of this agent as well as the protocol version
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class VersionHandler extends JsonRequestHandler<JmxVersionRequest> {

    private ServerHandle serverHandle;

    /**
     * Constructor
     *
     * @param pRestrictor access restrions
     * @param pServerHandle a server handle as obtained from a {@link org.jolokia.detector.ServerDetector}
     */
    public VersionHandler(Restrictor pRestrictor, ServerHandle pServerHandle) {
        super(pRestrictor);
        serverHandle = pServerHandle;
    }

    /** {@inheritDoc} */
    @Override
    public RequestType getType() {
        return RequestType.VERSION;
    }

    /** {@inheritDoc} */
    @Override
    protected void checkForRestriction(JmxVersionRequest pRequest) {
        checkType();
    }

    /** {@inheritDoc} */
    @Override
    public boolean handleAllServersAtOnce(JmxVersionRequest pRequest) {
        return true;
    }

    /** {@inheritDoc}
     * @param serverManager
     * @param request*/
    @Override
    public Object doHandleRequest(MBeanServerExecutor serverManager, JmxVersionRequest request) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        JSONObject ret = new JSONObject();
        ret.put("agent",Version.getAgentVersion());
        ret.put("protocol",Version.getProtocolVersion());
        if (serverHandle != null) {
            ret.put("info", serverHandle.toJSONObject(serverManager));
        }
        return ret;
    }

    /** {@inheritDoc} */
    @Override
    // Wont be called
    public Object doHandleRequest(MBeanServerConnection server, JmxVersionRequest request) {
        throw new UnsupportedOperationException("Internal: Method must not be called when all MBeanServers are handled at once");
    }

}
