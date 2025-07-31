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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.ops4j.pax.exam.Constants.START_LEVEL_SYSTEM_BUNDLES;
import static org.ops4j.pax.exam.Constants.START_LEVEL_TEST_BUNDLE;
import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.OptionUtils.combine;

/**
 * <p>Single base class for all Pax Exam integration tests. Subclasses may add specific helper methods.</p>
 *
 * <p>For now it's (almost) a duplicate of similar class from pax-web-itest-osgi, but the goal is to have one after
 * Pax Web 8 refactoring ends.</p>
 *
 * <p>{@link PerClass} strategy is needed. maven-failsafe-plugin's {@code reuseForks=false} and {@code forkCount=1} is
 * not enough to properly clean up JVM between methods and we may miss some URL handlers, etc. In other words - don't
 * use {@link org.ops4j.pax.exam.spi.reactors.PerMethod}.</p>
 *
 * <p>This class is part of a Maven module, which is just a jar, not a bundle. So it should be private-packaged
 * in any test-related bundle running in pax-exam-container-native/karaf.</p>
 */
@ExamReactorStrategy(PerClass.class)
public abstract class AbstractOsgiTestBase {

	public static final Logger LOG = LoggerFactory.getLogger("org.ops4j.pax.web.itest");
	public static final String PROBE_SYMBOLIC_NAME = "PaxExam-Probe";

	// location of where pax-logging-api will have output file written according to
	// "org.ops4j.pax.logging.useFileLogFallback" system/context property
	// filename will match test class name with ".log" extension
	protected static final File LOG_DIR = new File("target/logs-default");

	@Rule
	public TestName testName = new TestName();

	@Inject
	protected BundleContext context;

	@Before
	public void beforeEach() {
		LOG.info("========== Running {}.{}() ==========", getClass().getName(), testName.getMethodName());
	}

	@After
	public void afterEach() {
		LOG.info("========== Finished {}.{}() ==========", getClass().getName(), testName.getMethodName());
	}

	protected Option[] baseConfigure() {
		LOG_DIR.mkdirs();

        Option[] options = new Option[] {
				// basic options
				bootDelegationPackages("sun.*", "com.sun.*"),
				systemPackages("sun.misc", "com.sun.xml.bind.annotation;version=2.3"),

				frameworkStartLevel(START_LEVEL_TEST_BUNDLE),

				workingDirectory("target/paxexam"),
				// needed for PerClass strategy and I had problems running more test classes without cleaning
				// caches (timeout waiting for ProbeInvoker with particular UUID)
				cleanCaches(true),
				systemTimeout(60 * 60 * 1000),

				// set to "4" to see Felix wiring information
				frameworkProperty("felix.log.level").value("0"),

				// added implicitly by pax-exam, if pax.exam.system=test
				// these resources are provided inside org.ops4j.pax.exam:pax-exam-link-mvn jar
				// for example, "link:classpath:META-INF/links/org.ops4j.base.link" = "mvn:org.ops4j.base/ops4j-base/1.5.0"
				url("link:classpath:META-INF/links/org.ops4j.base.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
				url("link:classpath:META-INF/links/org.ops4j.pax.swissbox.core.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
				url("link:classpath:META-INF/links/org.ops4j.pax.swissbox.extender.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
				url("link:classpath:META-INF/links/org.ops4j.pax.swissbox.framework.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
				url("link:classpath:META-INF/links/org.ops4j.pax.swissbox.lifecycle.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
				url("link:classpath:META-INF/links/org.ops4j.pax.swissbox.tracker.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
				url("link:classpath:META-INF/links/org.ops4j.pax.exam.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
				url("link:classpath:META-INF/links/org.ops4j.pax.exam.inject.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),
				url("link:classpath:META-INF/links/org.ops4j.pax.extender.service.link").startLevel(START_LEVEL_SYSTEM_BUNDLES),

				// this bundle provides correct osgi.contract;osgi.contract=JavaInject
				linkBundle("org.apache.servicemix.bundles.javax-inject").startLevel(START_LEVEL_SYSTEM_BUNDLES),

				junitBundles(),

				mavenBundle("org.ops4j.pax.logging", "pax-logging-api")
						.versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1),
				mavenBundle("org.ops4j.pax.logging", "pax-logging-log4j2")
						.versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1)
		};

        options = combine(options, defaultLoggingConfig());
        options = combine(options, systemProperty("org.ops4j.pax.logging.property.file").value("src/test/resources/log4j2-osgi.properties"));

        return options;
	}

    /**
     * Configuring symbolic name in test probe we can easily locate related log entries in the output.
     * @param builder
     * @return
     */
    @ProbeBuilder
    public TestProbeBuilder probeBuilder(TestProbeBuilder builder) {
        builder.setHeader(Constants.BUNDLE_SYMBOLICNAME, PROBE_SYMBOLIC_NAME);
        return builder;
    }

	/**
	 * Reasonable defaults for default logging level (actually a threshold), framework logger level and usage
	 * of file-based default/fallback logger.
	 * @return
	 */
	protected Option[] defaultLoggingConfig() {
		String fileName;
		try {
			fileName = new File(LOG_DIR, getClass().getSimpleName() + ".log").getCanonicalPath();
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
			throw new RuntimeException(e.getMessage(), e);
		}

		return new Option[] {
				// every log with level higher or equal to DEBUG (i.e., not TRACE) will be logged
				frameworkProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("DEBUG"),
				// threshold for R7 Compendium 101.8 logging statements (from framework/bundle/service events)
				frameworkProperty("org.ops4j.pax.logging.service.frameworkEventsLogLevel").value("ERROR"),
				// default log will be written to file
				frameworkProperty("org.ops4j.pax.logging.useFileLogFallback").value(fileName)
		};
	}

	// --- methods that add logical sets of bundles (or just single bundles) to pax-exam-container-native

	/**
	 * Installation of 4 fundamental Jolokia bundles + Servlet API.
	 * @return
	 */
	protected Option[] jolokiaCore() {
		return new Option[] {
                mavenBundle("jakarta.servlet", "jakarta.servlet-api").versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1),
                mavenBundle("org.osgi", "org.osgi.service.servlet").versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1),
				mavenBundle("org.jolokia", "jolokia-json").versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1),
				mavenBundle("org.jolokia", "jolokia-server-core").versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1),
				mavenBundle("org.jolokia", "jolokia-service-jmx").versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1),
				mavenBundle("org.jolokia", "jolokia-service-serializer").versionAsInProject().startLevel(START_LEVEL_TEST_BUNDLE - 1)
		};
	}

	// --- helper methods to be used in all the tests

	/**
	 * Get a bundle by symbolic name.
	 * @param symbolicName
	 * @return
	 */
	protected Bundle bundle(String symbolicName) {
		return Arrays.stream(context.getBundles())
				.filter(b -> symbolicName.equals(b.getSymbolicName())).findFirst().orElse(null);
	}

	// --- helper methods to be used in all the tests

	protected int javaMajorVersion() {
		String v = System.getProperty("java.specification.version");
		if (v.contains(".")) {
			// before Java 9
			v = v.split("\\.")[1];
		}
		return Integer.parseInt(v);
	}

}
