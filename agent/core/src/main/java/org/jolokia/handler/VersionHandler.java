package org.jolokia.handler;

import java.util.HashMap;
import java.util.Map;

import org.jolokia.JmxRequest;
import org.jolokia.Version;
import org.jolokia.config.Restrictor;

import javax.management.*;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


/**
 * Get the version of this agent as well as the protocol version
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class VersionHandler extends JsonRequestHandler {

    public VersionHandler(Restrictor pRestrictor) {
        super(pRestrictor);
    }

    @Override
    public JmxRequest.Type getType() {
        return JmxRequest.Type.VERSION;
    }

    @Override
    public Object doHandleRequest(MBeanServerConnection server, JmxRequest request) {
        Map<String,String> ret = new HashMap<String, String>();
        ret.put("agent",Version.getAgentVersion());
        ret.put("protocol",Version.getProtocolVersion());
        return ret;
    }
}
