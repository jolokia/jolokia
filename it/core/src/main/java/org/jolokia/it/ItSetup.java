/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package org.jolokia.it;

import java.util.ArrayList;
import java.util.List;

import javax.management.ObjectName;

import org.jolokia.backend.MBeanServerHandler;

/**
 * @author roland
 * @since Mar 27, 2010
 */
public class ItSetup {

    private static final long serialVersionUID = 42L;

    private MBeanServerHandler mBeanHandler;

    private String domain = "jolokia.it";

    private String[] strangeNamesShort = {
            "simple",
            "/slash-simple/",
            "/--/",
            "with%3acolon",
            "//server/client",
            "service%3ajmx%3armi%3a///jndi/rmi%3a//bhut%3a9999/jmxrmi",
            "\"jdbc/testDB\""
//            "äöüßÄÖÜ"
    };
    private List<String> strangeNames = new ArrayList<String>();

    private String[] escapedNamesShort = {
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
              "???"
    };
    private List<String> escapedNames = new ArrayList<String>();


    private List<ObjectName> testBeans = new ArrayList<ObjectName>();

    public ItSetup() {
        mBeanHandler = new MBeanServerHandler("type=it");
    }

    public void start() {
        registerMBeans();
    }

    public void stop() {
        unregisterMBeans();
    }

    public static void premain(String agentArgs) {
        ItSetup itSetup = new ItSetup();
        itSetup.start();
    }
    // ===================================================================================================

    private void registerMBeans() {
        try {
            // Register my test mbeans
            for (String name : strangeNamesShort) {
                String strangeName = domain + ":type=naming,name=" + name;
                strangeNames.add(strangeName);
                registerMBean(new ObjectNameChecking(),strangeName);
            }
            for (String name : escapedNamesShort) {
                String escapedName = domain + ":type=escape,name=" + ObjectName.quote(name);
                escapedNames.add(escapedName);
                registerMBean(new ObjectNameChecking(),escapedName);
            }

            // Other MBeans
            boolean isWebsphere;
            try {
                Class.forName("com.ibm.websphere.management.AdminServiceFactory");
                isWebsphere = true;

            } catch (ClassNotFoundException exp) {
                isWebsphere = false;
            }
            registerMBean(new OperationChecking(),isWebsphere ? null : getOperationMBean());
            registerMBean(new AttributeChecking(),isWebsphere ? null : getAttributeMBean());

        } catch (RuntimeException e) {
            throw new RuntimeException("Error",e);
        } catch (Exception exp) {
            throw new RuntimeException("Error",exp);
        }
    }

    public String getAttributeMBean() {
        return domain + ":type=attribute";
    }

    public String getOperationMBean() {
        return domain + ":type=operation";
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private ObjectName registerMBean(Object pObject, String ... pName) {
        try {
            ObjectName oName = mBeanHandler.registerMBean(pObject,pName);
            System.out.println("Registered " + oName);
            testBeans.add(oName);
            return oName;
        } catch (RuntimeException e) {
            throw new RuntimeException("Cannot register MBean " + (pName != null && pName.length > 0 ? pName[0] : pObject),e);
        } catch (Exception e) {
            throw new RuntimeException("Cannot register MBean " + (pName != null && pName.length > 0 ? pName[0] : pObject),e);
        }
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private void unregisterMBeans() {
        for (ObjectName name : testBeans) {
            try {
                mBeanHandler.unregisterMBean(name);
            } catch (Exception e) {
                System.out.println("Exception while unregistering " + name + e);
            }
        }
    }

    public List<String> getStrangeNames() {
        return strangeNames;
    }

    public List<String> getEscapedNames() {
        return escapedNames;
    }
}
