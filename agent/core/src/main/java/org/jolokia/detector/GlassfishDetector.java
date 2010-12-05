/*
 * Copyright 2009-2010 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.detector;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanServer;

/**
 * Detector for Glassfish servers
 *
 * @author roland
 * @since 04.12.10
 */
public class GlassfishDetector extends AbstractServerDetector {

    private static final Pattern GLASSFISH_VERSION = Pattern.compile("^.*GlassFish.*\\sv?(.*?)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern GLASSFISH_FULL_VERSION = Pattern.compile("^\\s*GlassFish.*?\\s([.\\d]+)\\s.*$?");

    public ServerInfo detect(Set<MBeanServer> pMbeanServers) {
        String version = null;
        boolean amxBooted = false;
        String fullVersion = getSingleStringAttribute(pMbeanServers,"com.sun.appserv:j2eeType=J2EEServer,*","serverVersion");
        if (fullVersion != null) {
            if (GLASSFISH_VERSION.matcher(fullVersion).matches()) {
                // Ok, its a glassfish
                Matcher v2Matcher =  GLASSFISH_VERSION.matcher(fullVersion);
                if (v2Matcher.matches()) {
                    version = v2Matcher.group(1);
                }
            }
        }
        if (fullVersion == null || "3".equals(version)) {
            String versionFromAmx = getSingleStringAttribute(pMbeanServers,"amx:type=domain-root,*","ApplicationServerFullVersion");
            if (versionFromAmx != null) {
                // AMX already booted
                amxBooted = true;
                version = getVersionFromFullVersion(version,versionFromAmx);
            } else {
                amxBooted = false;
                version = getVersionFromFullVersion(version, System.getProperty("glassfish.version"));
            }
        } else {
            // Last desperate try to get hold of Glassfish MBean
            if (mBeanExists(pMbeanServers,"com.sun.appserver:type=Host,*")) {
                version = "3";
            }
        }
        if (version != null) {
            Map<String,String> extraInfo = null;
            if (version.startsWith("3")) {
                extraInfo = new HashMap<String,String>();
                extraInfo.put("amxBooted",Boolean.toString(amxBooted));
            }
            return new ServerInfo("Sun","glassfish",version,null,extraInfo);
        } else {
            return null;
        }
    }

    private String getVersionFromFullVersion(String pOriginalVersion,String pFullVersion) {
        if (pFullVersion == null) {
            return pOriginalVersion;
        }
        Matcher v3Matcher = GLASSFISH_FULL_VERSION.matcher(pFullVersion);
        if (v3Matcher.matches()) {
            return v3Matcher.group(1);
        } else {
            return pOriginalVersion;
        }
    }
}
