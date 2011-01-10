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

import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;

/**
 * Detector for Glassfish servers
 *
 * @author roland
 * @since 04.12.10
 */
public class GlassfishDetector extends AbstractServerDetector {

    private static final Pattern GLASSFISH_VERSION = Pattern.compile("^.*GlassFish.*\\sv?(.*?)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern GLASSFISH_FULL_VERSION = Pattern.compile("^\\s*GlassFish.*?\\sv?([.\\d]+)\\s.*$?");

    public ServerHandle detect(Set<MBeanServer> pMbeanServers) {
        String version = null;
        boolean amxBooted = false;
        String fullVersion = getSingleStringAttribute(pMbeanServers,"com.sun.appserv:j2eeType=J2EEServer,*","serverVersion");
        if (fullVersion != null) {
            Matcher matcher = GLASSFISH_VERSION.matcher(fullVersion);
            if (matcher.matches()) {
                version = matcher.group(1);
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
            if (hasAmx(version)) {
                extraInfo = new HashMap<String,String>();
                extraInfo.put("amxBooted",Boolean.toString(amxBooted));
            }
            return new GlassfishServerHandle("Sun","glassfish",version,null,extraInfo);
        } else {
            return null;
        }
    }

    private boolean hasAmx(String pVersion) {
        return pVersion.startsWith("3");
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

    private class GlassfishServerHandle extends ServerHandle {

        public GlassfishServerHandle(String vendor, String product, String version, URL agentUrl, Map<String, String> extraInfo) {
            super(vendor, product, version, agentUrl, extraInfo);
        }

        @Override
        public Map<String, String> getExtraInfo(Set<? extends MBeanServerConnection> pServers) {
            Map<String,String> extra = super.getExtraInfo(pServers);
            if (extra != null && hasAmx(getVersion())) {
                extra.put("amxBooted",Boolean.toString(mBeanExists(pServers,"amx:type=domain-root,*")));
            }
            return extra;
        }
    }
}
