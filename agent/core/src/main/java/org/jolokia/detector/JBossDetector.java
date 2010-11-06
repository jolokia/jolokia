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

import com.sun.tools.javac.resources.version;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.util.Set;

/**
 * @author roland
 * @since 06.11.10
 */
public class JBossDetector extends AbstractServerDetector {
    
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
        return null;
    }


    public int getPopularity() {
        return 80;
    }
}

/*
jboss.web:J2EEApplication=none,J2EEServer=none,j2eeType=WebModule,name=//localhost/jolokia --> path

*/