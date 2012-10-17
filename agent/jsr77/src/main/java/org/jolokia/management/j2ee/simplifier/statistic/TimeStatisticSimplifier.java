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
package org.jolokia.management.j2ee.simplifier.statistic;

import javax.management.j2ee.statistics.TimeStatistic;

public class TimeStatisticSimplifier<T extends TimeStatistic>
		extends
		StatisticSimplifier<T, StatisticAttributeExtractor<T, ? extends Object>> {
	protected TimeStatisticSimplifier(Class<T> type) {
		super(type);
		addExtractor("count", new StatisticAttributeExtractor<T, Long>() {
			public Long extract(T o) {
				return o.getCount();
			}
		});
		addExtractor("maxTime", new StatisticAttributeExtractor<T, Long>() {
			public Long extract(T o) {
				return o.getMaxTime();
			}
		});
		addExtractor("minTime", new StatisticAttributeExtractor<T, Long>() {
			public Long extract(T o) {
				return o.getMinTime();
			}
		});
		addExtractor("totalTime", new StatisticAttributeExtractor<T, Long>() {
			public Long extract(T o) {
				return o.getTotalTime();
			}
		});
	}

	@SuppressWarnings("unchecked")
	public TimeStatisticSimplifier() {
		this((Class<T>) TimeStatistic.class);
	}
}
