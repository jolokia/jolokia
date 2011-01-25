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

import javax.management.MBeanServer;

import java.lang.reflect.*;
import java.util.Set;

/**
 * A detector for jetty
 *
 * @author roland
 * @since 05.11.10
 */
public class JettyDetector extends AbstractServerDetector {



    public ServerHandle detect(Set<MBeanServer> pMbeanServers) {
        Class serverClass = getClass("org.mortbay.jetty.Server");
        if (serverClass != null) {
            return new ServerHandle("Mortbay", "jetty", getVersion(serverClass), null, null);
        }
        serverClass = getClass("org.eclipse.jetty.server.Server");
        if (serverClass != null) {
            return new ServerHandle("Eclipse", "jetty", getVersion(serverClass), null, null);
        }
        return null;
    }

    private String getVersion(Class serverClass) {
        try {
            Method method = serverClass.getMethod("getVersion");
            if (Modifier.isStatic(method.getModifiers())) {
                return (String) method.invoke(null);
            } else {
                Constructor ctr = serverClass.getConstructor();
                Object server = ctr.newInstance();
                return (String) method.invoke(server);
            }
        } catch (NoSuchMethodException e) {
        } catch (InvocationTargetException e) {
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return null;
    }

    // Go up the classloader stack to eventually find the server class. The WebAppClassLoader
    // hide the the server classes loader by the parent class loader.
    protected Class getClass(String pClassName) {
        ClassLoader loader = getClassLoader();
        do {
            try {
                return Class.forName(pClassName,false, loader);
            } catch (ClassNotFoundException e) {}
            loader = loader.getParent();
        } while (loader != null);
        return null;
    }

}
