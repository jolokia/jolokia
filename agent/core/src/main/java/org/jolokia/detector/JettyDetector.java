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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author roland
 * @since 05.11.10
 */
public class JettyDetector extends AbstractServerDetector {
    public ServerInfo detect(Set<MBeanServer> pMbeanServers) {
        for (String serverClassName : new String[] {"org.mortbay.jetty.Server", "org.eclipse.jetty.server.Server" }) {
            try {
                Class serverClass = getClass(serverClassName);
                if (serverClass != null) {
                    Method method = null;
                    method = serverClass.getMethod("getVersion");
                    String version = (String) method.invoke(null);
                    int major = extractMajorVersion(version);
                    return new ServerInfo(major < 7 ? "Mortbay" : "Eclipse","jetty",version,null,null);
                }
            } catch (NoSuchMethodException e) {
            } catch (InvocationTargetException e) {
            } catch (IllegalAccessException e) {
            }
        }
        return null;
    }

    // Go up the classloader stack to eventually find the server class
    protected Class getClass(String pClassName) {
        ClassLoader loader = getClassLoader();
        do {
            try {
                return Class.forName(pClassName,false, loader);
            } catch (ClassNotFoundException e) {}
        } while ( (loader = loader.getParent()) != null);
        return null;
    }

    public int getPopularity() {
        return 80;
    }

    private int extractMajorVersion(String version) {
        Pattern pattern = Pattern.compile("^(\\d+)");
        Matcher matcher = pattern.matcher(version);
        if (matcher.find()) {
            String majorS = matcher.group(1);
            return Integer.parseInt(majorS);
        } else {
            return 0;
        }
    }

}
