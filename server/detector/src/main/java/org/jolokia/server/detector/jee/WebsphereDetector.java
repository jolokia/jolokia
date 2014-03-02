package org.jolokia.server.detector.jee;

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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanServerConnection;

import org.jolokia.server.core.detector.DefaultServerHandle;
import org.jolokia.server.core.service.api.ServerHandle;
import org.jolokia.server.core.util.ClassUtil;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.json.simple.JSONObject;

/**
 * Detector for IBM Websphere 6 & 7 & 8
 *
 * @author roland
 * @since 29.11.10
 */
public class WebsphereDetector extends AbstractServerDetector {

    private static final Pattern SERVER_VERSION_PATTERN =
            Pattern.compile("^Version\\s+([0-9.]+)\\s*$.*?^Build Date\\s+([0-9/]+)\\s*$",
                            Pattern.MULTILINE | Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    public static final  String  INTERNAL_ERROR_MSG = "Internal: Found AdminServiceFactory but can not call methods on it (wrong WAS version ?)";

    // Whether running under Websphere
    private boolean isWebsphere = ClassUtil.checkForClass("com.ibm.websphere.management.AdminServiceFactory");
    private boolean isWebsphere7 = ClassUtil.checkForClass("com.ibm.websphere.management.AdminContext");
    private boolean isWebsphere6 = isWebsphere && !isWebsphere7;

    /**
     * Create a server detector
     *
     * @param pOrder of the detector (within the list of detectors)
     */
    public WebsphereDetector(int pOrder) {
        super("websphere",pOrder);
    }

    /** {@inheritDoc}
     * @param pMBeanServerAccess*/
    public ServerHandle detect(MBeanServerAccess pMBeanServerAccess) {
        String serverVersion =
                getSingleStringAttribute(pMBeanServerAccess, "*:j2eeType=J2EEServer,type=Server,*", "serverVersion");
        if (serverVersion != null && serverVersion.contains("WebSphere")) {
            Matcher matcher = SERVER_VERSION_PATTERN.matcher(serverVersion);
            if (matcher.find()) {
                String version = matcher.group(1);
                String date = matcher.group(2);
                JSONObject extraInfo = new JSONObject();
                if (date != null) {
                    extraInfo.put("buildDate",date);
                }
                return new WebsphereServerHandle(version,extraInfo.size() > 0 ? extraInfo : null);
            }
            return null;
        } else if (isWebsphere) {
            return new WebsphereServerHandle(isWebsphere6 ? "6" : "7 or 8",null);
        }
        return null;
    }

    @Override
    /** {@inheritDoc} */
    public Set<MBeanServerConnection> getMBeanServers() {
        try {
            /*
			 * this.mbeanServer = AdminServiceFactory.getMBeanFactory().getMBeanServer();
			 */
            Class adminServiceClass = getClass().getClassLoader().loadClass("com.ibm.websphere.management.AdminServiceFactory");
            Method getMBeanFactoryMethod = adminServiceClass.getMethod("getMBeanFactory", new Class[0]);
            Object mbeanFactory = getMBeanFactoryMethod.invoke(null);
            Method getMBeanServerMethod = mbeanFactory.getClass().getMethod("getMBeanServer", new Class[0]);
            return Collections.singleton((MBeanServerConnection) getMBeanServerMethod.invoke(mbeanFactory));
        }
        catch (ClassNotFoundException ex) {
            // Expected if not running under WAS
        }
        catch (InvocationTargetException ex) {
            // CNFE should be earlier
            throw new IllegalArgumentException(INTERNAL_ERROR_MSG,ex);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException(INTERNAL_ERROR_MSG,ex);
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException(INTERNAL_ERROR_MSG,ex);
        }
        return null;
    }

    // ==================================================================================

    /**
     * Server handle for Websphere platforms
     */
    static class WebsphereServerHandle extends DefaultServerHandle {

        private Map<String,String> extraInfo;

        /** {@inheritDoc} */
        public WebsphereServerHandle(String pVersion, Map<String, String> pExtrainfo) {
            super("IBM","websphere", pVersion);
            extraInfo = pExtrainfo;
        }

        @Override
        public Map<String, String> getExtraInfo(MBeanServerAccess pServerManager) {
            return extraInfo;
        }

        /*
        In Jolokia 1.x it was possible to set a workaround for WebSphere 6 where websphere add a
        random extra part to an MBean name when registering via MBeanRegistration. Since Websphere 6
        is the only server exhibiting such a strange behaviour, and the workaround is quite evolved,
        it is removed for 2.0

        The code and the original comment is left here for reference, but will be removed soon.

        @Override
        public ObjectName registerMBeanAtServer(MBeanServer pServer, Object pMBean, String pName)
                throws MBeanRegistrationException, InstanceAlreadyExistsException,
                       NotCompliantMBeanException, MalformedObjectNameException {
            // Websphere adds extra parts to the object name if registered explicitly, but
            // we need a defined name on the client side. So we register it with 'null' in websphere
            // and let the bean define its name.
            if (isWebsphere6 && pMBean instanceof MBeanRegistration) {
                return pServer.registerMBean(pMBean,null).getObjectName();
            } else {
                return super.registerMBeanAtServer(pServer, pMBean, pName);
            }
        }
        */
    }
}
