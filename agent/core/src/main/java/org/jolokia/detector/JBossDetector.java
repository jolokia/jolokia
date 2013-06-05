package org.jolokia.detector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.util.ClassUtil;

/**
 * Detector for JBoss
 *
 * @author roland
 * @since 06.11.10
 */
public class JBossDetector extends AbstractServerDetector {

    /** {@inheritDoc} */
    public ServerHandle detect(MBeanServerExecutor pMBeanServerExecutor) {
        if (ClassUtil.checkForClass("org.jboss.mx.util.MBeanServerLocator")) {
            // Get Version number from JSR77 call
            String version = getVersionFromJsr77(pMBeanServerExecutor);
            if (version != null) {
                int idx = version.indexOf(' ');
                if (idx >= 0) {
                    // Strip off boilerplate
                    version = version.substring(0, idx);
                }
                return new JBossServerHandle(version, null, null);
            }
        }
        if (mBeanExists(pMBeanServerExecutor, "jboss.system:type=Server")) {
            String versionFull = getAttributeValue(pMBeanServerExecutor, "jboss.system:type=Server", "Version");
            String version = null;
            if (versionFull != null) {
                version = versionFull.replaceAll("\\(.*", "").trim();
            }
            return new JBossServerHandle(version, null, null);
        }
        String version = getSingleStringAttribute(pMBeanServerExecutor, "jboss.as:management-root=server", "releaseVersion");
        if (version != null) {
            return new JBossServerHandle(version, null, null);
        }
        if (mBeanExists(pMBeanServerExecutor, "jboss.modules:*")) {
            // It's a JBoss 7, probably a 7.0.x one ...
            return new JBossServerHandle("7", null, null);
        }
        return null;
    }

    // Special handling for JBoss

    @Override
    /** {@inheritDoc} */
    public void addMBeanServers(Set<MBeanServerConnection> servers) {
        try {
            Class locatorClass = Class.forName("org.jboss.mx.util.MBeanServerLocator");
            Method method = locatorClass.getMethod("locateJBoss");
            servers.add((MBeanServer) method.invoke(null));
        } catch (ClassNotFoundException e) { /* Ok, its *not* JBoss 4,5 or 6, continue with search ... */ } catch (NoSuchMethodException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }
    }

    // ========================================================================
    private static class JBossServerHandle extends ServerHandle {
        /**
         * JBoss server handle
         *
         * @param version             JBoss version
         * @param agentUrl            URL to the agent
         * @param extraInfo           extra ifo to return
          */
        JBossServerHandle(String version, URL agentUrl, Map<String, String> extraInfo) {
            super("RedHat", "jboss", version, agentUrl, extraInfo);
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