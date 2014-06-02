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

import javax.management.j2ee.statistics.ServletStats;
import javax.management.j2ee.statistics.TimeStatistic;

public class ServletStatsSimplifier<T extends ServletStats> extends
		StatsSimplifier<T, StatsAttributeExtractor<T, ? extends Object>> {
	protected ServletStatsSimplifier(Class<T> type) {
		super(type);
		addExtractor("messageCount",
				new StatsAttributeExtractor<T, TimeStatistic>() {
					public TimeStatistic extract(T o) {
						return o.getServiceTime();
					}
				});
	}

	@SuppressWarnings("unchecked")
	public ServletStatsSimplifier() {
		this((Class<T>) ServletStats.class);
	}
}
