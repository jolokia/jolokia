package org.jolokia.detector;

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

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.config.Configuration;
import org.jolokia.request.JmxRequest;
import org.jolokia.util.LogHandler;
import org.json.simple.JSONObject;

/**
 * Detector for Glassfish servers
 *
 * @author roland
 * @since 04.12.10
 */
public class GlassfishDetector extends AbstractServerDetector {

    private static final Pattern GLASSFISH_VERSION = Pattern.compile("^.*Glassfish.*\\sv?(.*?)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern GLASSFISH_FULL_VERSION = Pattern.compile("^.*Glassfish.*?\\sv?([.\\d]+).*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYARA_VERSION = Pattern.compile("^.*Payara.*\\sv?(.*?)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYARA_FULL_VERSION = Pattern.compile("^.*Payara.*?\\sv?([.\\d]+).*$",Pattern.CASE_INSENSITIVE);

    private static final String GLASSFISH_NAME = "glassfish";
    private static final String GLASSFISH_VENDOR_NAME = "Oracle";
    private static final String PAYARA_NAME = "Payara Server";
    private static final String PAYARA_VENDOR_NAME = "Payara Foundation";

    private String serverName;
    private String vendorName;

    /** {@inheritDoc}
     * @param pMBeanServerExecutor*/
    public ServerHandle detect(MBeanServerExecutor pMBeanServerExecutor) {
        String version = detectVersion(pMBeanServerExecutor);
        if (version!= null) {
            return new GlassfishServerHandle(version, new HashMap<String, String>());
        } else {
            return null;
        }
    }

    private String detectVersion(MBeanServerExecutor pMBeanServerExecutor) {
        String fullVersion = getSingleStringAttribute(pMBeanServerExecutor,"com.sun.appserv:j2eeType=J2EEServer,*","serverVersion");
        String version = extractVersionFromFullVersion(fullVersion);
        if (fullVersion == null || "3".equals(version)) {
            String versionFromAmx = getSingleStringAttribute(pMBeanServerExecutor,"amx:type=domain-root,*","ApplicationServerFullVersion");
            version =
                    getVersionFromFullVersion(
                            version,versionFromAmx != null ?
                                     versionFromAmx :
                                     System.getProperty("glassfish.version")
                    );
        } else if (mBeanExists(pMBeanServerExecutor,"com.sun.appserver:type=Host,*")) {
            // Last desperate try to get hold of Glassfish MBean
            version = "3";
        }
        return version;
    }

    // Tries to match Glassfish first, then Payara Server, updating server and vendor name
    private String extractVersionFromFullVersion(String pFullVersion) {
        if (pFullVersion != null) {
            Matcher matcher = GLASSFISH_VERSION.matcher(pFullVersion);
            if (matcher.matches()) {
                serverName = GLASSFISH_NAME;
                vendorName = GLASSFISH_VENDOR_NAME;
                return matcher.group(1);
            }
            matcher = PAYARA_VERSION.matcher(pFullVersion);
            if (matcher.matches()) {
                serverName = PAYARA_NAME;
                vendorName = PAYARA_VENDOR_NAME;
                return matcher.group(1);
            }
        }
        return null;
    }

    private boolean isAmxBooted(MBeanServerExecutor pServerManager) {
        return mBeanExists(pServerManager,"amx:type=domain-root,*");
    }

    // Return true if AMX could be booted, false otherwise
    private synchronized boolean bootAmx(MBeanServerExecutor pServers, final LogHandler pLoghandler) {
        ObjectName bootMBean = null;
        try {
            bootMBean = new ObjectName("amx-support:type=boot-amx");
        } catch (MalformedObjectNameException e) {
            // Cannot happen ....
        }
        try {
            pServers.call(bootMBean, new MBeanServerExecutor.MBeanAction<Void>() {
                /** {@inheritDoc} */
                public Void execute(MBeanServerConnection pConn, ObjectName pName, Object ... extraArgs)
                        throws ReflectionException, InstanceNotFoundException, IOException, MBeanException {
                    pConn.invoke(pName, "bootAMX", null, null);
                    return null;
                }
            });
            return true;
        } catch (InstanceNotFoundException e) {
            pLoghandler.error("No bootAmx MBean found: " + e,e);
            // Can happen, when a call to bootAmx comes to early before the bean
            // is registered
            return false;
        } catch (IllegalArgumentException e) {
            pLoghandler.error("Exception while booting AMX: " + e,e);
            // We dont try it again
            return true;
        } catch (Exception e) {
            pLoghandler.error("Exception while executing bootAmx: " + e, e);
            // dito
            return true;
        }
    }

    private String getVersionFromFullVersion(String pOriginalVersion,String pFullVersion) {
        if (pFullVersion == null) {
            return pOriginalVersion;
        }
        Matcher v3Matcher = GLASSFISH_FULL_VERSION.matcher(pFullVersion);
        Matcher payaraMatcher = PAYARA_FULL_VERSION.matcher(pFullVersion);
        if (v3Matcher.matches()) {
            serverName = GLASSFISH_NAME;
            vendorName = GLASSFISH_VENDOR_NAME;
            return v3Matcher.group(1);
        } else if (payaraMatcher.matches()) {
            serverName = PAYARA_NAME;
            vendorName = PAYARA_VENDOR_NAME;
            return payaraMatcher.group(1);
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
         * @param extraInfo extra infos
         */
        public GlassfishServerHandle(String version, Map<String, String> extraInfo) {
            super(vendorName, serverName, version, extraInfo);
        }

        @Override
        /** {@inheritDoc} */
        public Map<String, String> getExtraInfo(MBeanServerExecutor pServerManager) {
            Map<String,String> extra = super.getExtraInfo(pServerManager);
            if (pServerManager != null && extra != null && getVersion().startsWith("3")) {
                extra.put("amxBooted",Boolean.toString(isAmxBooted(pServerManager)));
            }
            return extra;
        }

        @Override
        /** {@inheritDoc} */
        public void preDispatch(MBeanServerExecutor pMBeanServerExecutor, JmxRequest pJmxReq) {
            if (amxShouldBeBooted) {
                // Clear flag only of bootAMX succeed or fails with an unrecoverable error
                amxShouldBeBooted = !bootAmx(pMBeanServerExecutor,logHandler);
            }
        }

        @Override
        /** {@inheritDoc} */
        public void postDetect(MBeanServerExecutor pServerManager,
                               Configuration pConfig, LogHandler pLoghandler) {
            JSONObject opts = getDetectorOptions(pConfig,pLoghandler);
            amxShouldBeBooted = (opts == null || opts.get("bootAmx") == null || (Boolean) opts.get("bootAmx"))
                                && !isAmxBooted(pServerManager);
            logHandler = pLoghandler;
        }
    }


}
