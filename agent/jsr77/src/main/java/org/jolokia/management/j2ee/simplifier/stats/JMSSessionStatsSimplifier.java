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

import javax.management.j2ee.statistics.CountStatistic;
import javax.management.j2ee.statistics.JMSConsumerStats;
import javax.management.j2ee.statistics.JMSProducerStats;
import javax.management.j2ee.statistics.JMSSessionStats;
import javax.management.j2ee.statistics.TimeStatistic;

public class JMSSessionStatsSimplifier
		extends
		StatsSimplifier<JMSSessionStats, StatsAttributeExtractor<JMSSessionStats, ? extends Object>> {
	protected JMSSessionStatsSimplifier(Class<JMSSessionStats> type) {
		super(type);
		addExtractor(
				"consumers",
				new StatsAttributeExtractor<JMSSessionStats, JMSConsumerStats[]>() {
					public JMSConsumerStats[] extract(JMSSessionStats o) {
						return o.getConsumers();
					}
				});
		addExtractor("durableSubscriptionCount",
				new StatsAttributeExtractor<JMSSessionStats, CountStatistic>() {
					public CountStatistic extract(JMSSessionStats o) {
						return o.getDurableSubscriptionCount();
					}
				});
		addExtractor("expiredMessageCount",
				new StatsAttributeExtractor<JMSSessionStats, CountStatistic>() {
					public CountStatistic extract(JMSSessionStats o) {
						return o.getExpiredMessageCount();
					}
				});
		addExtractor("messageCount",
				new StatsAttributeExtractor<JMSSessionStats, CountStatistic>() {
					public CountStatistic extract(JMSSessionStats o) {
						return o.getMessageCount();
					}
				});
		addExtractor("messageWaitTime",
				new StatsAttributeExtractor<JMSSessionStats, TimeStatistic>() {
					public TimeStatistic extract(JMSSessionStats o) {
						return o.getMessageWaitTime();
					}
				});
		addExtractor("pendingMessageCount",
				new StatsAttributeExtractor<JMSSessionStats, CountStatistic>() {
					public CountStatistic extract(JMSSessionStats o) {
						return o.getPendingMessageCount();
					}
				});
		addExtractor(
				"producers",
				new StatsAttributeExtractor<JMSSessionStats, JMSProducerStats[]>() {
					public JMSProducerStats[] extract(JMSSessionStats o) {
						return o.getProducers();
					}
				});
	}

	public JMSSessionStatsSimplifier() {
		this(JMSSessionStats.class);
	}
}
