/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.server.core.service.impl;

import java.io.IOException;
import javax.management.JMException;

import org.jolokia.json.JSONObject;
import org.jolokia.server.core.Version;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.EmptyResponseException;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.service.api.AbstractJolokiaService;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.request.RequestHandler;
import org.jolokia.server.core.util.RequestType;

public class ConfigRequestHandler extends AbstractJolokiaService<RequestHandler> implements RequestHandler {

    private JolokiaContext context;

    ConfigRequestHandler() {
        super(RequestHandler.class, -2);
    }

    @Override
    public void init(JolokiaContext pJolokiaContext) {
        context = pJolokiaContext;
    }

    @Override
    public boolean canHandle(JolokiaRequest pJolokiaRequest) {
        return pJolokiaRequest.getType() == RequestType.CONFIG;
    }

    @Override
    public <R extends JolokiaRequest> Object handleRequest(R pJmxReq, Object pPreviousResult) throws JMException, IOException, NotChangedException, EmptyResponseException {
        // This endpoint is supposed to return "discovery" information and is expected to NOT be protected
        // so be careful what you return here
        // for now (https://github.com/jolokia/jolokia/issues/870) we want to return a list of supported
        // authentication mechanisms
        //
        // this endpoint is kind of an endpoint that could be exposed under `/.well-known/` prefix

        JSONObject ret = new JSONObject();
        ret.put("agent", Version.getAgentVersion());
        ret.put("protocol", Version.getProtocolVersion());
        ret.put("id", context.getConfig(ConfigKey.AGENT_ID));

        ret.put("security", context.getSecurityDetails().toJSONObject());

        return ret;
    }

    @Override
    public String getProvider() {
        return null;
    }

    @Override
    public Object getRuntimeInfo() {
        return null;
    }

}
