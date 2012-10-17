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

import java.util.HashMap;
import java.util.Map;

import javax.management.j2ee.statistics.Statistic;
import javax.management.j2ee.statistics.Stats;

import org.jolokia.management.j2ee.simplifier.AbstractSimplifier;

public class StatsSimplifier<T extends Stats, E extends StatsAttributeExtractor<T, ? extends Object>>
		extends AbstractSimplifier<T, E> {
	@SuppressWarnings("unchecked")
	protected StatsSimplifier(Class<T> type) {
		super(type);
		addExtractor(
				"statistics",
				(E) new StatsAttributeExtractor<Stats, Map<String, Statistic>>() {
					public Map<String, Statistic> extract(Stats o) {
						Map<String, Statistic> result = new HashMap<String, Statistic>();
						for (String name : o.getStatisticNames()) {
							result.put(name, o.getStatistic(name));
						}
						return result;
					}
				});
	}

	@SuppressWarnings("unchecked")
	public StatsSimplifier() {
		this((Class<T>) Stats.class);
	}
}
