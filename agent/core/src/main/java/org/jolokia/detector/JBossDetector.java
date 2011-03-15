/*
 * Copyright 2009-2010 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.detector;

import javax.management.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Map;
import java.util.Set;

import org.jolokia.request.JmxRequest;

/**
 * @author roland
 * @since 06.11.10
 */
public class JBossDetector extends AbstractServerDetector {

    public ServerHandle detect(Set<MBeanServer> pMbeanServers) {
        if (checkForClass("org.jboss.mx.util.MBeanServerLocator")) {
            // Get Version number from JR77 call
            String version = getVersionFromJsr77(pMbeanServers);
            if (version != null) {
                int idx = version.indexOf(' ');
                if (idx >= 0) {
                    // Strip off boilerplate
                    version = version.substring(0,idx);
                }
                return new JBossServerHandle(version,null,null);
            }
        }
        if (mBeanExists(pMbeanServers, "jboss.system:type=Server")) {
            String versionFull = getAttributeValue(pMbeanServers, "jboss.system:type=Server","Version");
            String version = null;
            if (versionFull != null) {
                version = versionFull.replaceAll("\\(.*", "").trim();
            }
            return new JBossServerHandle(version,null,null);
        }

        return null;
    }

    // Special handling for JBoss
    @Override
    public void addMBeanServers(Set<MBeanServer> servers) {
        try {
            Class locatorClass = Class.forName("org.jboss.mx.util.MBeanServerLocator");
            Method method = locatorClass.getMethod("locateJBoss");
            servers.add((MBeanServer) method.invoke(null));
        }
        catch (ClassNotFoundException e) { /* Ok, its *not* JBoss, continue with search ... */ }
        catch (NoSuchMethodException e) { }
        catch (IllegalAccessException e) { }
        catch (InvocationTargetException e) { }
    }

    // ========================================================================
    private static class JBossServerHandle extends ServerHandle {

        public JBossServerHandle(String version, URL agentUrl, Map<String, String> extraInfo) {
            super("JBoss", "jboss", version, agentUrl, extraInfo);
        }

        @Override
        public void preDispatch(Set<MBeanServer> pMBeanServers, JmxRequest pJmxReq) {
            if (pJmxReq.getObjectName() != null &&
                    "java.lang".equals(pJmxReq.getObjectName().getDomain())) {
            try {
                // invoking getMBeanInfo() works around a bug in getAttribute() that fails to
                // refetch the domains from the platform (JDK) bean server (e.g. for MXMBeans)
                for (MBeanServer s : pMBeanServers) {
                    try {
                        s.getMBeanInfo(pJmxReq.getObjectName());
                        return;
                    } catch (InstanceNotFoundException exp) {
                        // Only one server can have the name. So, this exception
                        // is being expected to happen
                    }
                }
            } catch (IntrospectionException e) {
                throw new IllegalStateException("Workaround for JBoss failed for object " + pJmxReq.getObjectName() + ": " + e);
            } catch (ReflectionException e) {
                throw new IllegalStateException("Workaround for JBoss failed for object " + pJmxReq.getObjectName() + ": " + e);
            }
        }
    }

    }
}
/*
jboss.web:J2EEApplication=none,J2EEServer=none,j2eeType=WebModule,name=//localhost/jolokia --> path
jboss.web:name=HttpRequest1,type=RequestProcessor,worker=http-bhut%2F172.16.239.130-8080 --> remoteAddr, serverPort, protocol
*/