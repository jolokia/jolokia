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

import javax.management.MBeanServer;
import java.util.Set;

/**
 * @author roland
 * @since 06.11.10
 */
public class JBossDetector extends AbstractServerDetector {

    private boolean useClassLookup = true;

    public ServerInfo detect(Set<MBeanServer> pMbeanServers) {
        if (checkForClass("org.jboss.mx.util.MBeanServerLocator")) {
            // Get Version number from JR77 call
            String version = getVersionFromJsr77(pMbeanServers);
            if (version != null) {
                int idx = version.indexOf(" ");
                if (idx >= 0) {
                    // Strip off boilerplate
                    version = version.substring(0,idx);
                }
                return new ServerInfo("JBoss","jboss",version,null,null);
            }
        }
        if (mBeanExists(pMbeanServers, "jboss.system:type=Server")) {
            String versionFull = getAttributeValue(pMbeanServers, "jboss.system:type=Server","Version");
            String version = null;
            if (versionFull != null) {
                version = versionFull.replaceAll("\\(.*", "").trim();
            }
            return new ServerInfo("JBoss","jboss",version,null,null);
        }

        return null;
    }
}
/*
jboss.web:J2EEApplication=none,J2EEServer=none,j2eeType=WebModule,name=//localhost/jolokia --> path
jboss.web:name=HttpRequest1,type=RequestProcessor,worker=http-bhut%2F172.16.239.130-8080 --> remoteAddr, serverPort, protocol
*/