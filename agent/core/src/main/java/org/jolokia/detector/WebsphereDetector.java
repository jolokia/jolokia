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

import org.jolokia.util.ClassUtil;
import org.json.simple.JSONObject;

import javax.management.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detector for IBM Websphere 6 & 7
 *
 * @author roland
 * @since 29.11.10
 */
public class WebsphereDetector extends AbstractServerDetector {

    private static final Pattern SERVER_VERSION_PATTERN =
            Pattern.compile("^Version\\s+([0-9.]+)\\s*$.*?^Build Date\\s+([0-9/]+)\\s*$",
                    Pattern.MULTILINE | Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    // Whether running under Websphere
    private boolean isWebsphere = ClassUtil.checkForClass("com.ibm.websphere.management.AdminServiceFactory");
    private boolean isWebsphere7 = ClassUtil.checkForClass("com.ibm.websphere.management.AdminContext");
    private boolean isWebsphere6 = isWebsphere && !isWebsphere7;

    public ServerHandle detect(Set<MBeanServer> pMbeanServers) {
        String platformName =
                getSingleStringAttribute(pMbeanServers, "*:j2eeType=J2EEServer,type=Server,*", "platformName");
        if (platformName != null && platformName.contains("WebSphere")) {
            String serverVersion =
                    getSingleStringAttribute(pMbeanServers, "*:j2eeType=J2EEServer,type=Server,*", "serverVersion");
            if (serverVersion != null) {
                Matcher matcher = SERVER_VERSION_PATTERN.matcher(serverVersion);
                if (matcher.find()) {
                    String version = matcher.group(1);
                    String date = matcher.group(2);
                    JSONObject extraInfo = new JSONObject();
                    if (date != null) {
                        extraInfo.put("buildDate",date);
                    }
                    // TODO: Extract access URL
                    return new WebsphereServerHandle(version,null,extraInfo.size() > 0 ? extraInfo : null);
                }
            }
            return null;
        } else if (isWebsphere) {
            return new WebsphereServerHandle(isWebsphere6 ? "6" : "7",null,null);
        }
        return null;
    }

    @Override
    public void addMBeanServers(Set<MBeanServer> servers) {
        try {
            /*
			 * this.mbeanServer = AdminServiceFactory.getMBeanFactory().getMBeanServer();
			 */
            Class adminServiceClass = getClass().getClassLoader().loadClass("com.ibm.websphere.management.AdminServiceFactory");
            Method getMBeanFactoryMethod = adminServiceClass.getMethod("getMBeanFactory", new Class[0]);
            Object mbeanFactory = getMBeanFactoryMethod.invoke(null);
            Method getMBeanServerMethod = mbeanFactory.getClass().getMethod("getMBeanServer", new Class[0]);
            servers.add((MBeanServer) getMBeanServerMethod.invoke(mbeanFactory));
        }
        catch (ClassNotFoundException ex) {
            // Expected if not running under WAS
        }
        catch (InvocationTargetException ex) {
            // CNFE should be earlier
            throw new IllegalArgumentException("Internal: Found AdminServiceFactory but can not call methods on it (wrong WAS version ?)",ex);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException("Internal: Found AdminServiceFactory but can not call methods on it (wrong WAS version ?)",ex);
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException("Internal: Found AdminServiceFactory but can not call methods on it (wrong WAS version ?)",ex);
        }
    }

    // ==================================================================================

    class WebsphereServerHandle extends ServerHandle {

        public WebsphereServerHandle(String pVersion, URL pAgenturl, Map<String, String> pExtrainfo) {
            super("IBM","websphere", pVersion, pAgenturl, pExtrainfo);
        }

        @Override
        public ObjectName registerMBeanAtServer(MBeanServer pServer, Object pMBean, String pName) throws MBeanRegistrationException, InstanceAlreadyExistsException, NotCompliantMBeanException, MalformedObjectNameException {
            // Websphere adds extra parts to the object name if registered explicitly, but
            // we need a defined name on the client side. So we register it with 'null' in websphere
            // and let the bean define its name.
            if (isWebsphere6 && pMBean instanceof MBeanRegistration) {
                return pServer.registerMBean(pMBean,null).getObjectName();
            } else {
                return super.registerMBeanAtServer(pServer, pMBean, pName);
            }
        }
    }
}
