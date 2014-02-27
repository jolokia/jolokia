package org.jolokia.service.jmx.detector;

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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.*;

import org.jolokia.core.request.JolokiaRequest;
import org.jolokia.core.service.detector.DefaultServerHandle;
import org.jolokia.core.service.detector.ServerHandle;
import org.jolokia.core.util.jmx.MBeanServerAccess;
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

    /**
     * Create a server detector
     *
     * @param pOrder of the detector (within the list of detectors)
     */
    public GlassfishDetector(int pOrder) {
        super(pOrder);
    }

    /** {@inheritDoc}
     * @param pMBeanServerAccess*/
    public ServerHandle detect(MBeanServerAccess pMBeanServerAccess) {
        String version = detectVersion(pMBeanServerAccess);
        if (version!= null) {
            return new GlassfishServerHandle(version,amxShouldBeBooted(pMBeanServerAccess));
        } else {
            return null;
        }
    }

    private String detectVersion(MBeanServerAccess pMBeanServerAccess) {
        String fullVersion = getSingleStringAttribute(pMBeanServerAccess, "com.sun.appserv:j2eeType=J2EEServer,*", "serverVersion");
        String version = extractVersionFromFullVersion(fullVersion);
        if (fullVersion == null || "3".equals(version)) {
            String versionFromAmx = getSingleStringAttribute(pMBeanServerAccess,"amx:type=domain-root,*","ApplicationServerFullVersion");
            version =
                    getVersionFromFullVersion(
                            version,versionFromAmx != null ?
                                     versionFromAmx :
                                     System.getProperty("glassfish.version")
                    );
        } else if (mBeanExists(pMBeanServerAccess,"com.sun.appserver:type=Host,*")) {
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

    private boolean amxShouldBeBooted(MBeanServerAccess pMBeanServerAccess) {
        JSONObject opts = getDetectorOptions("glassfish");
        return (opts == null || opts.get("bootAmx") == null || (Boolean) opts.get("bootAmx"))
               && !isAmxBooted(pMBeanServerAccess);
    }

    private boolean isAmxBooted(MBeanServerAccess pServerManager) {
        return mBeanExists(pServerManager,"amx:type=domain-root,*");
    }

    // Return true if AMX could be booted, false otherwise
    private synchronized boolean bootAmx(MBeanServerAccess pServers) {
        ObjectName bootMBean = null;
        try {
            bootMBean = new ObjectName("amx-support:type=boot-amx");
        } catch (MalformedObjectNameException e) {
            // Cannot happen ....
        }
        try {
            pServers.call(bootMBean, new MBeanServerAccess.MBeanAction<Void>() {
                /** {@inheritDoc} */
                public Void execute(MBeanServerConnection pConn, ObjectName pName, Object ... extraArgs)
                        throws ReflectionException, InstanceNotFoundException, IOException, MBeanException {
                    pConn.invoke(pName, "bootAMX", null, null);
                    return null;
                }
            });
            return true;
        } catch (InstanceNotFoundException e) {
            jolokiaContext.error("No bootAmx MBean found: " + e, e);
            // Can happen, when a call to bootAmx comes to early before the bean
            // is registered
            return false;
        } catch (IllegalArgumentException e) {
            jolokiaContext.error("Exception while booting AMX: " + e, e);
            // We dont try it again
            return true;
        } catch (Exception e) {
            jolokiaContext.error("Exception while executing bootAmx: " + e, e);
            // dito
            return true;
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

    private class GlassfishServerHandle extends DefaultServerHandle {
        private boolean amxShouldBeBooted;

        /**
         * Server handle for a glassfish server
         *
         * @param version Glassfish version
         */
        public GlassfishServerHandle(String version,boolean pAmxShouldBeBooted) {
            super("Oracle", "glassfish", version);
            amxShouldBeBooted = pAmxShouldBeBooted;
        }

        @Override
        /** {@inheritDoc} */
        public Map<String, String> getExtraInfo(MBeanServerAccess pExecutor) {
            Map<String,String> extra = new HashMap<String, String>();
            if (extra != null && getVersion().startsWith("3")) {
                extra.put("amxBooted",Boolean.toString(isAmxBooted(pExecutor)));
            }
            return extra;
        }

        @Override
        /** {@inheritDoc} */
        public void preDispatch(MBeanServerAccess pExecutor, JolokiaRequest pJmxReq) {
            if (amxShouldBeBooted) {
                // Clear flag only of bootAMX succeed or fails with an unrecoverable error
                amxShouldBeBooted = !bootAmx(pExecutor);
            }
        }
    }


}
