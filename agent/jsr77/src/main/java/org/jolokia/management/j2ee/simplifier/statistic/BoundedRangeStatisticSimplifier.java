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

import javax.management.j2ee.statistics.BoundedRangeStatistic;

public class BoundedRangeStatisticSimplifier<T extends BoundedRangeStatistic>
		extends BoundaryStatisticSimplifier<T> {
	protected BoundedRangeStatisticSimplifier(Class<T> type) {
		super(type);
		addExtractor("lowerBound", new StatisticAttributeExtractor<T, Long>() {
			public Long extract(T o) {
				return o.getLowerBound();
			}
		});
		addExtractor("upperBound", new StatisticAttributeExtractor<T, Long>() {
			public Long extract(T o) {
				return o.getUpperBound();
			}
		});
		addExtractor("current", new StatisticAttributeExtractor<T, Long>() {
			public Long extract(T o) {
				return o.getCurrent();
			}
		});
		addExtractor("highWaterMark",
				new StatisticAttributeExtractor<T, Long>() {
					public Long extract(T o) {
						return o.getHighWaterMark();
					}
				});
		addExtractor("lowWaterMark",
				new StatisticAttributeExtractor<T, Long>() {
					public Long extract(T o) {
						return o.getLowWaterMark();
					}
				});
	}

	@SuppressWarnings("unchecked")
	public BoundedRangeStatisticSimplifier() {
		this((Class<T>) BoundedRangeStatistic.class);
	}
}
