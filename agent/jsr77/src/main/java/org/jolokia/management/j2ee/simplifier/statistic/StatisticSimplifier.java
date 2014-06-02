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

import javax.management.j2ee.statistics.Statistic;

import org.jolokia.management.j2ee.simplifier.AbstractSimplifier;

public class StatisticSimplifier<T extends Statistic, E extends StatisticAttributeExtractor<T, ? extends Object>>
		extends AbstractSimplifier<T, E> {
	@SuppressWarnings("unchecked")
	protected StatisticSimplifier(Class<T> type) {
		super(type);
		addExtractor("lastSampleTime",
				(E) new StatisticAttributeExtractor<T, Long>() {
					public Long extract(T o) {
						return o.getLastSampleTime();
					}
				});
		addExtractor("name", (E) new StatisticAttributeExtractor<T, String>() {
			public String extract(T o) {
				return o.getName();
			}
		});
		addExtractor("startTime",
				(E) new StatisticAttributeExtractor<T, Long>() {
					public Long extract(T o) {
						return o.getStartTime();
					}
				});
		addExtractor("unit", (E) new StatisticAttributeExtractor<T, String>() {
			public String extract(T o) {
				return o.getUnit();
			}
		});
	}

	@SuppressWarnings("unchecked")
	public StatisticSimplifier() {
		this((Class<T>) Statistic.class);
	}
}
