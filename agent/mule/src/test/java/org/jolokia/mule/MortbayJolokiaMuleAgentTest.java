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

import org.mule.api.lifecycle.InitialisationException;

/**
 * @author Michio Nakagawa
 * @since 10.10.14
 */
public class MortbayJolokiaMuleAgentTest extends JolokiaMuleAgentTestCase {
	@Override
	protected JolokiaMuleAgent createJolokiaMuleAgent() {
		return new JolokiaMuleAgent() {
	    	@Override
	    	public void initialise() throws InitialisationException {
	    		this.server = new MortbayMuleAgentHttpServer(this, this);
	    	};
	    };
	}
}
