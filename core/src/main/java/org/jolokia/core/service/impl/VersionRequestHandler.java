package org.jolokia.core.service.impl;

import java.io.IOException;
import java.util.Set;

import javax.management.JMException;

import org.jolokia.core.Version;
import org.jolokia.core.config.ConfigKey;
import org.jolokia.core.request.JolokiaRequest;
import org.jolokia.core.request.NotChangedException;
import org.jolokia.core.service.*;
import org.jolokia.core.service.request.RequestHandler;
import org.jolokia.core.util.RequestType;
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
public class VersionRequestHandler extends AbstractJolokiaService<RequestHandler> implements RequestHandler {

    // Context from where to get information
    private JolokiaContext context;

    /**
     * Constructor
     */
    VersionRequestHandler() {
        // Must be always first
        super(RequestHandler.class,0);
    }

    @Override
    public void init(JolokiaContext pJolokiaContext) {
        context = pJolokiaContext;
    }

    public Object handleRequest(JolokiaRequest pJmxReq, Object pPreviousResult) throws JMException, IOException, NotChangedException {
        JSONObject ret = new JSONObject();
        ret.put("agent", Version.getAgentVersion());
        ret.put("protocol",Version.getProtocolVersion());
        ret.put("id",context.getConfig(ConfigKey.AGENT_ID));
        AgentDetails agentDetails = context.getAgentDetails();
        if (agentDetails != null) {
            ret.put("details", agentDetails.toJSONObject());
        }

        // Each request handler adds an extra information
        JSONObject info = new JSONObject();

        for (RequestHandler handler : context.getServices(RequestHandler.class)) {
            // Skip myself
            if (handler == this) {
                continue;
            }
            Object rtInfo = handler.getRuntimeInfo();
            info.put(handler.getRealm(),rtInfo != null ? rtInfo : new JSONObject());
        }
        ret.put("info",info);
        ret.put("config", configToJSONObject());

        return ret;
    }

    /** {@inheritDoc} */
    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        return pJolokiaRequest.getType() == RequestType.VERSION;
    }

    // ========================================================================

    private JSONObject configToJSONObject() {
        JSONObject info = new JSONObject();
        Set<ConfigKey> keys = context.getConfigKeys();
        for (ConfigKey key : keys) {
                if (key.isGlobalConfig()) {
                    info.put(key.getKeyValue(), context.getConfig(key));
                }
        }
        return info;
    }

    // Not used here
    public String getRealm() {
        return null;
    }

    public Object getRuntimeInfo() {
        return null;
    }
}
