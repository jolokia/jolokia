/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.it.osgi;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.ops4j.pax.exam.OptionUtils.combine;

/**
 * Clean OSGi test. Just simplest showcase of what's needed to run manually controlled pax-exam test.
 */
@RunWith(PaxExam.class)
public class CleanIntegrationTest extends AbstractOsgiTestBase {

	public static final Logger LOG = LoggerFactory.getLogger(CleanIntegrationTest.class);

	@Configuration
	public Option[] configure() {
		return combine(baseConfigure(), jolokiaCore());
	}

	@Test
	public void justRun() {
		Set<Bundle> bundles = new TreeSet<>((b1, b2) -> (int) (b1.getBundleId() - b2.getBundleId()));
		bundles.addAll(Arrays.asList(context.getBundles()));
		for (Bundle b : bundles) {
			String info = String.format("#%02d: %s/%s (%s)",
					b.getBundleId(), b.getSymbolicName(), b.getVersion(), b.getLocation());
			LOG.info(info);
		}

        for (String bundle : new String[] {
            "org.jolokia.json",
            "org.jolokia.server.core",
            "org.jolokia.service.serializer",
            "org.jolokia.service.jmx" }) {
            Bundle b = bundle(bundle);
            assertThat(b.getState(), equalTo(Bundle.ACTIVE));
        }
	}

}
