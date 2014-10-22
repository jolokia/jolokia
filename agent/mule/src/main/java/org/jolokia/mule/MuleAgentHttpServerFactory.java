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

import org.mule.api.agent.Agent;

/**
 * HTTP Server factory for the Mule agent.
 *
 * 
 * @author Michio Nakagawa
 * @since 10.10.14
 */
public final class MuleAgentHttpServerFactory {
	// Server class for Jetty6
	static String CLAZZ_NAME = "org.mortbay.jetty.Server";
	
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
		MuleAgentHttpServer server = null;

		try {
			Class.forName(CLAZZ_NAME);
			server = new MortbayMuleAgentHttpServer(pParent, pConfig);	
		} catch (ClassNotFoundException e) {
			server = new EclipseMuleAgentHttpServer(pParent, pConfig);
		}		
		return server;	
	}
}
