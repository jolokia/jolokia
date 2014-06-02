/*
 *  Copyright 2012 Marcin Plonka
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
 * @author mplonka
 */
package org.jolokia.management.j2ee.simplifier.stats;

import javax.management.j2ee.statistics.JCAConnectionStats;
import javax.management.j2ee.statistics.TimeStatistic;

public class JCAConnectionStatsSimplifier<T extends JCAConnectionStats> extends
		StatsSimplifier<T, StatsAttributeExtractor<T, ? extends Object>> {
	protected JCAConnectionStatsSimplifier(Class<T> type) {
		super(type);
		addExtractor("connectionFactory",
				new StatsAttributeExtractor<T, String>() {
					public String extract(T o) {
						return o.getConnectionFactory();
					}
				});
		addExtractor("managedConnectionFactory",
				new StatsAttributeExtractor<T, String>() {
					public String extract(T o) {
						return o.getManagedConnectionFactory();
					}
				});
		addExtractor("useTime",
				new StatsAttributeExtractor<T, TimeStatistic>() {
					public TimeStatistic extract(T o) {
						return o.getUseTime();
					}
				});
		addExtractor("waitTime",
				new StatsAttributeExtractor<T, TimeStatistic>() {
					public TimeStatistic extract(T o) {
						return o.getWaitTime();
					}
				});
	}

	@SuppressWarnings("unchecked")
	public JCAConnectionStatsSimplifier() {
		this((Class<T>) JCAConnectionStats.class);
	}
}
