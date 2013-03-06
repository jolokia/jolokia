package org.jolokia.it;

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

import java.lang.management.ManagementFactory;
import java.util.*;

import javax.management.*;

import org.jolokia.jmx.JolokiaMBeanServerUtil;

/**
 * @author roland
 * @since Mar 27, 2010
 */
public class ItSetup {

    public static final  String JOLOKIA_IT_DOMAIN           = "jolokia.it";
    public static final  String JOLOKIA_IT_DOMAIN_HIDDEN    = "jolokia.it.hidden";
    private static final String JOLOKIA_IT_JSONMBEAN_DOMAIN = "jolokia.it.jsonmbean";

    private String[]     strangeNamesShort = {
            "\\/",
            "simple",
            "/slash-simple/",
            "/--/",
            "with%3acolon",
            "//server/client",
            "service%3ajmx%3armi%3a///jndi/rmi%3a//bhut%3a9999/jmxrmi",
            "\"jdbc/testDB\"",
            "name with space",
            "n!a!m!e with !/!",
//            "äöüßÄÖÜ"
    };
    private List<String> strangeNames      = new ArrayList<String>();

    private String[] fullNames = {
            "jolokia/it:id=3786439,pid=[ServiceRegistryProvider#(null)],type=ParticipantMonitor"
    };

    private String[]     escapedNamesShort = {
//            "name*with?strange=\"chars"
            "name*withstrange=chars",
            "name?withstrange=chars",
            "namewithstrange=\"chars\"",
            "namewithstrange:\"chars\"",
            ",,,",
            ",,/,,",
            "===",
            "***",
            "\"\"\"",
            ":::",
            "???",
            "!!!"
    };
    private List<String> escapedNames      = new ArrayList<String>();


    private List<ObjectName> registeredMBeans;
    private List<ObjectName> registeredJolokiaMBeans;

    public ItSetup() {
    }

    public void start() {
        registeredMBeans = registerMBeans(ManagementFactory.getPlatformMBeanServer(), JOLOKIA_IT_DOMAIN);
        MBeanServer jolokiaServer = getJolokiaMBeanServer();
        if (jolokiaServer != null) {
            registeredJolokiaMBeans = registerMBeans(jolokiaServer, JOLOKIA_IT_DOMAIN_HIDDEN);
            registeredJolokiaMBeans.addAll(registerJsonMBeans(jolokiaServer, JOLOKIA_IT_JSONMBEAN_DOMAIN));
        }
    }

    public void stop() {
        unregisterMBeans(registeredMBeans, ManagementFactory.getPlatformMBeanServer());
        MBeanServer jolokiaServer = getJolokiaMBeanServer();
        if (jolokiaServer != null) {
            unregisterMBeans(registeredJolokiaMBeans, jolokiaServer);
        }
    }

    public static void premain(String agentArgs) {
        ItSetup itSetup = new ItSetup();
        itSetup.start();
    }
    // ===================================================================================================

    private List<ObjectName> registerMBeans(MBeanServer pServer, String pDomain) {
        List<ObjectName> ret = new ArrayList<ObjectName>();
        try {
            // Register my test mbeans
            for (String name : strangeNamesShort) {
                String strangeName = pDomain + ":type=naming/,name=" + name;
                strangeNames.add(strangeName);
                ret.add(registerMBean(pServer, new ObjectNameChecking(strangeName), strangeName));
            }
            for (String name : escapedNamesShort) {
                String escapedName = pDomain + ":type=escape,name=" + ObjectName.quote(name);
                escapedNames.add(escapedName);
                ret.add(registerMBean(pServer, new ObjectNameChecking(escapedName), escapedName));
            }

            // Other MBeans with different names
            for (String name : fullNames) {
                ret.add(registerMBean(pServer, new ObjectNameChecking(name), name));
            }
                        
            // Other MBeans
            boolean isWebsphere = checkForClass("com.ibm.websphere.management.AdminServiceFactory");
            ret.add(registerMBean(pServer,new OperationChecking(JOLOKIA_IT_DOMAIN),isWebsphere ? null : pDomain + ":type=operation"));
            ret.add(registerMBean(pServer, new AttributeChecking(JOLOKIA_IT_DOMAIN), isWebsphere ? null : pDomain + ":type=attribute"));
            // MXBean
            if (hasMxBeanSupport()) {
                ret.add(registerMBean(pServer, new MxBeanSample(), isWebsphere ? null : pDomain + ":type=mxbean"));
            }
            // Tabular Data MBean
            ret.add(registerMBean(pServer, new TabularMBean(), pDomain + ":type=tabularData"));
        } catch (RuntimeException e) {
            throw new RuntimeException("Error",e);
        } catch (Exception exp) {
            throw new RuntimeException("Error",exp);
        }
        return ret;
    }

    private List<ObjectName> registerJsonMBeans(MBeanServer pServer, String pDomain) {
        List<ObjectName> ret = new ArrayList<ObjectName>();
        try {
            ret.add(registerMBean(pServer,new JsonChecking(),pDomain + ":type=plain"));
            ret.add(registerMXBean(pServer, new JsonChecking2(), JsonChecking2MXBean.class, pDomain + ":type=mx"));
            return ret;
        } catch (Exception e) {
            throw new RuntimeException("Error",e);
        } catch (NoSuchMethodError e) {
            // Happens for Java 5 .. will ignore it here
            return ret;
        }
    }


    private boolean hasMxBeanSupport() {
        return checkForClass("javax.management.MXBean") && ! checkForClass("org.jboss.mx.util.MBeanServerLocator");
    }

    public String getAttributeMBean() {
        return JOLOKIA_IT_DOMAIN + ":type=attribute";
    }

    public String getOperationMBean() {
        return JOLOKIA_IT_DOMAIN + ":type=operation";
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private void unregisterMBeans(List<ObjectName> pMBeanNames,MBeanServer pServer) {
        for (ObjectName name : pMBeanNames) {
            try {
                pServer.unregisterMBean(name);
            } catch (Exception e) {
                System.out.println("Exception while unregistering " + e);
            }
        }
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private ObjectName registerMBean(MBeanServer pServer, Object pObject, String pName)
            throws MalformedObjectNameException, MBeanRegistrationException, InstanceAlreadyExistsException, NotCompliantMBeanException {
        ObjectName registeredName = pName != null ?
                pServer.registerMBean(pObject, new ObjectName(pName)).getObjectName() :
                pServer.registerMBean(pObject,null).getObjectName();
        System.out.println("Registered " + registeredName);
        return registeredName;
    }

    // Needed, because the JBoss MBeanServer cannot parse MXBean interfaces
    // Hence we wrap it with a StandardMBean
    // See https://community.jboss.org/thread/167796 for details
    private ObjectName registerMXBean(MBeanServer pServer, Object pObject, Class pManagementInterface, String pName) throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        ObjectName objectName = new ObjectName(pName);
        final StandardMBean mxBean = new StandardMBean(pObject, pManagementInterface, true /* MXBean */);
        pServer.registerMBean(mxBean, objectName);
        System.out.println("Registered MXBean " + objectName);
        return objectName;
    }

    public List<String> getStrangeNames() {
        return strangeNames;
    }

    public List<String> getEscapedNames() {
        return escapedNames;
    }

    private boolean checkForClass(String pClassName) {
        return getClass(pClassName) != null;
    }

    private Class getClass(String pClassName) {
        try {
            ClassLoader loader = getClassLoader();
            return Class.forName(pClassName,false, loader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    public MBeanServer getJolokiaMBeanServer() {
        try {
            Class.forName("org.jolokia.jmx.JolokiaMBeanServerUtil");
            return JolokiaMBeanServerUtil.getJolokiaMBeanServer();
        } catch (RuntimeException e) {
            System.out.println("No JolokiaServer found ....");
            return null;
        } catch (ClassNotFoundException e) {
            System.out.println("No JolokiaServer found, ignoring certain tests ...");
            return null;
        }
    }
}
