package org.jolokia.server.detector.jee;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import javax.management.MBeanServerConnection;

import org.jolokia.server.core.detector.DefaultServerHandle;
import org.jolokia.server.core.service.api.ServerHandle;
import org.jolokia.server.core.util.ClassUtil;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;

/**
 * Detector for JBoss
 *
 * @author roland
 * @since 06.11.10
 */
public class JBossDetector extends AbstractServerDetector {

    /**
     * Create a server detector
     *
     * @param pOrder of the detector (within the list of detectors)
     */
    public JBossDetector(int pOrder) {
        super("jboss",pOrder);
    }

    public static final String JBOSS_AS_MANAGEMENT_ROOT_SERVER = "jboss.as:management-root=server";

    /** {@inheritDoc} */
    public ServerHandle detect(MBeanServerAccess pMBeanServerAccess) {
        ServerHandle handle = checkFromJSR77(pMBeanServerAccess);
        if (handle == null) {
            handle = checkFor5viaJMX(pMBeanServerAccess);
            if (handle == null) {
                handle = checkForManagementRootServerViaJMX(pMBeanServerAccess);
            }
            if (handle == null) {
                handle = checkForWildflySwarm();
            }
            if (handle == null) {
                handle = fallbackForVersion7Check(pMBeanServerAccess);
            }
        }
        return handle;
    }

    private ServerHandle checkForWildflySwarm() {
        if (isJBossModulesBasedContainer(this.getClass().getClassLoader())) {
            if (System.getProperties().containsKey("swarm.app.artifact")) {
                String version = System.getProperty("swarm.version");
                return new JBossServerHandle(version != null ? version : "unknown", "Wildfly Swarm");
            }
        }
        return null;
    }

    /**
     * Attempts to detect a JBoss modules based application server. Because getting
     * access to the main arguments is not possible, it returns true in case the system property
     * {@code jboss.modules.system.pkgs} is set and the {@code org/jboss/modules/Main.class} resource can be found
     * using the class loader of this class.
     *
     * If so, it awaits the early initialization of a JBoss modules based application server by polling the system property
     * {@code java.util.logging.manager} and waiting until the specified class specified by this property has been
     * loaded by the JVM.
     */
    @Override
    public ClassLoader jvmAgentStartup(Instrumentation instrumentation) {
        jvmAgentStartup(instrumentation, this.getClass().getClassLoader());

        return null;
    }

    void jvmAgentStartup(Instrumentation instrumentation, ClassLoader classLoader) {
        if (isJBossModulesBasedContainer(classLoader)) {
            awaitServerInitializationForJBossModulesBasedContainer(instrumentation);
        }
    }

    protected boolean isJBossModulesBasedContainer(ClassLoader classLoader) {
        return hasWildflyProperties() &&
               // Contained in any JBoss modules app:
               classLoader.getResource("org/jboss/modules/Main.class") != null;
    }

    private boolean hasWildflyProperties() {
        // For Wildfly AS:
        if (System.getProperty("jboss.modules.system.pkgs") != null) {
            return true;
        }
        // For Wildfly Swarm:
        String bootModuleLoader = System.getProperty("boot.module.loader");
        if (bootModuleLoader != null) {
            return bootModuleLoader.contains("wildfly");
        }
        return false;
    }

    // Wait a max 5 Minutes
    public static final int LOGGING_DETECT_TIMEOUT = 5 * 60 * 1000;
    public static final int LOGGING_DETECT_INTERVAL = 200;

    @Override
    protected int getDetectionTimeout() {
        return LOGGING_DETECT_TIMEOUT;
    }

    @Override
    protected int getDetectionInterval() {
        return LOGGING_DETECT_INTERVAL;
    }

    private void awaitServerInitializationForJBossModulesBasedContainer(Instrumentation instrumentation) {
        activeWait(instrumentation,
            () -> {
                String loggingManagerClassName = System.getProperty("java.util.logging.manager");
                if (loggingManagerClassName != null) {
                    if (isClassLoaded(loggingManagerClassName, instrumentation)) {
                        // Assuming that the logging manager (most likely org.jboss.logmanager.LogManager)
                        // is loaded by the static initializer of java.util.logging.LogManager (and not by
                        // other code), we know now that either the java.util.logging.LogManager singleton
                        // is or will be initialized.
                        // Here is the trigger for loading the class:
                        // https://github.com/jboss-modules/jboss-modules/blob/1.5.1.Final/src/main/java/org/jboss/modules/Main.java#L482
                        // Therefore the steps 3-6 of the proposal for option 2 don't need to be performed,
                        // see https://github.com/rhuss/jolokia/issues/258 for details.
                        return true;
                    }
                }
                return false;
            },
            "Detected JBoss Module loader, but property java.util.logging.manager is not set after %d seconds");
    }

    private ServerHandle checkFromJSR77(MBeanServerAccess pMBeanServerAccess) {
        if (ClassUtil.checkForClass("org.jboss.mx.util.MBeanServerLocator")) {
            // Get Version number from JSR77 call
            String version = getVersionFromJsr77(pMBeanServerAccess);
            if (version != null) {
                int idx = version.indexOf(' ');
                if (idx >= 0) {
                    // Strip off boilerplate
                    version = version.substring(0, idx);
                }
                return new JBossServerHandle(version);
            }
        }
        return null;
    }

    private ServerHandle checkFor5viaJMX(MBeanServerAccess pMBeanServerAccess) {
        if (mBeanExists(pMBeanServerAccess, "jboss.system:type=Server")) {
            String versionFull = getAttributeValue(pMBeanServerAccess, "jboss.system:type=Server", "Version");
            String version = null;
            if (versionFull != null) {
                version = versionFull.replaceAll("\\(.*", "").trim();
            }
            return new JBossServerHandle(version);
        }
        return null;
    }

    private ServerHandle checkForManagementRootServerViaJMX(MBeanServerAccess pMBeanServerAccess) {
        // Bug (or not ?) in Wildfly 8.0: Search for jboss.as:management-root=server return null but accessing this
        // MBean works. So we are looking, whether the JMX domain jboss.as exists and fetch the version directly.
        if (!searchMBeans(pMBeanServerAccess, "jboss.as:*").isEmpty()) {
            String version = getAttributeValue(pMBeanServerAccess, JBOSS_AS_MANAGEMENT_ROOT_SERVER, "productVersion");
            if (version == null) {
                version = getAttributeValue(pMBeanServerAccess, JBOSS_AS_MANAGEMENT_ROOT_SERVER, "releaseVersion");
            }
            if (version != null) {
                String product = getAttributeValue(pMBeanServerAccess, JBOSS_AS_MANAGEMENT_ROOT_SERVER, "productName");
                return new JBossServerHandle(version, product != null ? product : "jboss");
            }
        }
        return null;
    }

    private ServerHandle fallbackForVersion7Check(MBeanServerAccess pMBeanServerAccess) {
        if (mBeanExists(pMBeanServerAccess, "jboss.modules:*")) {
            // It's a JBoss 7, probably a 7.0.x one ...
            return new JBossServerHandle("7");
        }
        return null;
    }

    @Override
    public Set<MBeanServerConnection> getMBeanServers() {
        try {
            Class<?> locatorClass = Class.forName("org.jboss.mx.util.MBeanServerLocator");
            Method method = locatorClass.getMethod("locateJBoss");
            return Collections.singleton((MBeanServerConnection) method.invoke(null));
        } catch (ClassNotFoundException e) {
            /* Ok, its *not* JBoss 4,5 or 6, continue with search ... */
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
        return null;
    }

    // Special handling for JBoss


    // ========================================================================
    private static class JBossServerHandle extends DefaultServerHandle {
        /**
         * JBoss server handle, with custom name
         *
         * @param version             JBoss version
         * @param name                Product name to use
         */
        JBossServerHandle(String version, String name) {
            super("RedHat", name, version);
        }

        /**
         * JBoss server handle, using "jboss" as product name
         *
         * @param version             JBoss version
         */
        JBossServerHandle(String version) {
            this(version, "jboss");
        }
    }
}
/*
 * Copyright 2009-2013 Roland Huss
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

/*
jboss.web:J2EEApplication=none,J2EEServer=none,j2eeType=WebModule,name=//localhost/jolokia --> path
jboss.web:name=HttpRequest1,type=RequestProcessor,worker=http-bhut%2F172.16.239.130-8080 --> remoteAddr, serverPort, protocol
*/
