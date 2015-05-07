package org.jolokia.mule;

/*
 * Copyright 2014 Michio Nakagawa
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

import org.jolokia.util.ClassUtil;
import org.mule.api.agent.Agent;

/**
 * HTTP Server factory for the Mule agent.
 *
 * 
 * @author Michio Nakagawa
 * @since 10.10.14
 */
public final class MuleAgentHttpServerFactory {

    protected MuleAgentHttpServerFactory() {}

	/**
     * Create the internal HTTP server.
     * Create the Jetty Server of Mortbay packages or Eclipse Foundation packages.
     * 
	 * 
     * @param pParent parent for creating proper exceptions
     * @param pConfig configuration of the server
	 * @return internal HTTP server
	 */
	public static MuleAgentHttpServer create(Agent pParent, MuleAgentConfig pConfig) {
        if (ClassUtil.checkForClass("org.mortbay.jetty.Server")) {
            return new MortbayMuleAgentHttpServer(pParent, pConfig);
        } else if (ClassUtil.checkForClass("org.eclipse.jetty.server.ServerConnector")) {
            return new Jetty9MuleAgentHttpServer(pParent, pConfig);
        } else if (ClassUtil.checkForClass("org.eclipse.jetty.server.Server")) {
            return new Jetty7And8MuleAgentHttpServer(pParent, pConfig);
        }
        throw new IllegalStateException("Cannot detect Jetty version (tried 6,7,8,9)");
    }
}
