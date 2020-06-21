package org.jolokia.config.address;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
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
 * Delegating {@link AddressConfigService} wicht iterates over multiple service
 * to optain an valid {@link InetAddress} to bind the <em>Jolokia</em> service
 * to.
 * 
 * 
 * @author Georg Tsakumagos
 * @since 21.06.2020
 */
public class DelegatingAddressConfigService implements AddressConfigService {

	private final List<AddressConfigService> services;

	/**
	 * Konstructor
	 */
	public DelegatingAddressConfigService() {
		super();

		this.services = Collections.unmodifiableList(Arrays.asList(new AddressConfigService[] {
				new DirectAddressConfigService(), new IPMatchingConfigService(), new NICMatchingConfigService() }));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AtomicReference<InetAddress> optain(Map<String, String> agentConfig) {
		final Iterator<AddressConfigService> iterator = this.services.iterator();

		while (iterator.hasNext()) {
			AtomicReference<InetAddress> result = iterator.next().optain(agentConfig);

			if (null != result) {
				return result;
			}
		}

		try {
			return new AtomicReference<InetAddress>(InetAddress.getByName(null));
		} catch (final UnknownHostException exception) {
			throw new IllegalArgumentException("Can not lookup loopback interface", exception);
		}
	}
}
