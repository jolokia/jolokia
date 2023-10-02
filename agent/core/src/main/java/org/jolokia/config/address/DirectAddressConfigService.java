package org.jolokia.config.address;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/*
 * 
 * Copyright 2020 Georg Tsakumagos
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
 * Direct service using the {@value #CONFIG_KEY} configuration to optain an
 * {@link InetAddress}
 * 
 * @author Georg Tsakumagos
 * @since 21.06.2020
 */
public class DirectAddressConfigService implements AddressConfigService {

    static final String CONFIG_KEY = "host";
    
	/**
	 * {@inheritDoc}
	 */
	@Override
	public final AtomicReference<InetAddress> optain(final Map<String, String> agentConfig) {
		final String host = agentConfig.get(CONFIG_KEY);

		if (agentConfig.containsKey(CONFIG_KEY)) {
		    // Mark responsibility to the callee
			final AtomicReference<InetAddress> result = new AtomicReference<InetAddress>();
			try {
				if ("*".equals(host) || "0.0.0.0".equals(host)) {
					return result; // null is the wildcard
				} else {
					result.set(InetAddress.getByName(host)); // some specific host
				}
			} catch (UnknownHostException e) {
				throw new IllegalArgumentException(
						"Can not lookup " + (host != null ? host : "loopback interface") + ": " + e, e);
			}
			return result;
		}
		return null;
	}

}
