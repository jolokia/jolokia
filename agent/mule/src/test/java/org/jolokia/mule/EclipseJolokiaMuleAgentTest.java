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
import org.mule.api.lifecycle.InitialisationException;

/**
 * @author Michio Nakagawa
 * @since 10.10.14
 */
public class EclipseJolokiaMuleAgentTest extends JolokiaMuleAgentTestCase {
	@Override
	protected JolokiaMuleAgent createJolokiaMuleAgent() {
		return new JolokiaMuleAgent() {
	    	@Override
	    	public void initialise() throws InitialisationException {
                if (ClassUtil.checkForClass(Jetty9MuleAgentHttpServer.SERVER_CONNECTOR_CLASS)) {
                    this.server = new Jetty9MuleAgentHttpServer(this, this);
                    System.out.println("Testing Jetty 9");
                } else if (ClassUtil.checkForClass("org.eclipse.jetty.server.Server")) {
                    this.server = new Jetty7And8MuleAgentHttpServer(this,this);
                    System.out.println("Testing Jetty 7/8");
                } else {
                    throw new IllegalStateException("Cannot detect neither Jetty 7/8 or Jetty9");
                }
	    	};
	    };
	}
}
