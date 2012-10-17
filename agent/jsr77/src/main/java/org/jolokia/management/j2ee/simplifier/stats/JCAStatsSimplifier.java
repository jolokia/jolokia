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

import javax.management.j2ee.statistics.JCAConnectionPoolStats;
import javax.management.j2ee.statistics.JCAConnectionStats;
import javax.management.j2ee.statistics.JCAStats;

public class JCAStatsSimplifier
		extends
		StatsSimplifier<JCAStats, StatsAttributeExtractor<JCAStats, ? extends Object>> {
	protected JCAStatsSimplifier(Class<JCAStats> type) {
		super(type);
		addExtractor(
				"connectionPools",
				new StatsAttributeExtractor<JCAStats, JCAConnectionPoolStats[]>() {
					public JCAConnectionPoolStats[] extract(JCAStats o) {
						return o.getConnectionPools();
					}
				});
		addExtractor("connections",
				new StatsAttributeExtractor<JCAStats, JCAConnectionStats[]>() {
					public JCAConnectionStats[] extract(JCAStats o) {
						return o.getConnections();
					}
				});
	}

	public JCAStatsSimplifier() {
		this(JCAStats.class);
	}
}
