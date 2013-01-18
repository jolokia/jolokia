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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.*;

import org.jolokia.backend.MBeanServerManager;
import org.jolokia.request.JmxRequest;
import org.jolokia.util.ConfigKey;
import org.jolokia.util.LogHandler;
import org.json.simple.JSONObject;

/**
 * Detector for Glassfish servers
 *
 * @author roland
 * @since 04.12.10
 */
public class GlassfishDetector extends AbstractServerDetector {

    private static final Pattern GLASSFISH_VERSION = Pattern.compile("^.*GlassFish.*\\sv?(.*?)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern GLASSFISH_FULL_VERSION = Pattern.compile("^.*GlassFish.*?\\sv?([.\\d]+).*$",Pattern.CASE_INSENSITIVE);

    /** {@inheritDoc}
     * @param pMBeanServerManager*/
    public ServerHandle detect(MBeanServerManager pMBeanServerManager) {
        String version = detectVersion(pMBeanServerManager);
        if (version!= null) {
            return new GlassfishServerHandle(version,null,new HashMap<String, String>());
        } else {
            return null;
        }
    }

    private String detectVersion(MBeanServerManager pMBeanServerManager) {
        String fullVersion = getSingleStringAttribute(pMBeanServerManager,"com.sun.appserv:j2eeType=J2EEServer,*","serverVersion");
        String version = extractVersionFromFullVersion(fullVersion);
        if (fullVersion == null || "3".equals(version)) {
            String versionFromAmx = getSingleStringAttribute(pMBeanServerManager,"amx:type=domain-root,*","ApplicationServerFullVersion");
            version =
                    getVersionFromFullVersion(
                            version,versionFromAmx != null ?
                                     versionFromAmx :
                                     System.getProperty("glassfish.version")
                    );
        } else if (mBeanExists(pMBeanServerManager,"com.sun.appserver:type=Host,*")) {
            // Last desperate try to get hold of Glassfish MBean
            version = "3";
        }
        return version;
    }

    private String extractVersionFromFullVersion(String pFullVersion) {
        if (pFullVersion != null) {
            Matcher matcher = GLASSFISH_VERSION.matcher(pFullVersion);
            if (matcher.matches()) {
               return matcher.group(1);
            }
        }
        return null;
    }

    private boolean isAmxBooted(MBeanServerManager pServerManager) {
        return mBeanExists(pServerManager,"amx:type=domain-root,*");
    }

    private void bootAmx(MBeanServerManager pServers, LogHandler pLoghandler) {
        ObjectName bootMBean = null;
        try {
            bootMBean = new ObjectName("amx-support:type=boot-amx");
        } catch (MalformedObjectNameException e) {
            // Cannot happen ....
        }
        InstanceNotFoundException infExp = null;
        for (MBeanServerConnection server : pServers.getAllMBeanServers()) {
            try {
                server.invoke(bootMBean, "bootAMX", null, null);
                return;
            } catch (InstanceNotFoundException e) {
                // Can be the case if multiple MBeanServers has been found, next try ...
                infExp = e;
            } catch (Exception e) {
                pLoghandler.error("Exception while executing bootAmx: " + e, e);
            }
        }
        if (infExp != null) {
            pLoghandler.error("No bootAmx MBean found: ",infExp);
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

    private class GlassfishServerHandle extends ServerHandle {
        private boolean amxShouldBeBooted = false;
        private LogHandler logHandler;

        /**
         * Server handle for a glassfish server
         *
         * @param version Glassfish version
         * @param agentUrl agent url
         * @param extraInfo extra infos
         */
        public GlassfishServerHandle(String version, URL agentUrl, Map<String, String> extraInfo) {
            super("Oracle", "glassfish", version, agentUrl, extraInfo);
        }

        /** {@inheritDoc}
         * @param pServerManager*/
        @Override
        public Map<String, String> getExtraInfo(MBeanServerManager pServerManager) {
            Map<String,String> extra = super.getExtraInfo(pServerManager);
            if (extra != null && getVersion().startsWith("3")) {
                extra.put("amxBooted",Boolean.toString(isAmxBooted(pServerManager)));
            }
            return extra;
        }

        @Override
        public void preDispatch(MBeanServerManager pMBeanServerManager, JmxRequest pJmxReq) {
            if (amxShouldBeBooted) {
                bootAmx(pMBeanServerManager,logHandler);
                amxShouldBeBooted = false;
            }
        }

        /** {@inheritDoc} */
        @Override
        public void postDetect(MBeanServerManager pServerManager,
                               Map<ConfigKey, String> pConfig, LogHandler pLoghandler) {
            JSONObject opts = getDetectorOptions(pConfig,pLoghandler);
            amxShouldBeBooted = (opts == null || opts.get("bootAmx") == null || (Boolean) opts.get("bootAmx"))
                                && !isAmxBooted(pServerManager);
            logHandler = pLoghandler;
        }
    }


}
