package org.jolokia.jvmagent.client.util;

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

import java.io.File;
import java.net.*;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Utility class for looking up a class within tools.jar
 *
 * @author roland
 * @since 07.10.11
 */
public final class ToolsClassFinder {

    private ToolsClassFinder() {}

    private static final String JAVA_HOME = System.getProperty("java.home");

    // Location to look for tools.jar
    private static final File[] TOOLS_JAR_LOCATIONS = new File[] {
                    new File(JAVA_HOME + "/../lib/tools.jar"),
                    new File(JAVA_HOME + "/lib/tools.jar")
            };

    /**
     * Lookup a class and return its definition. This is first tried by the current classloader (which
     * has loaded this class) and if this fails by searching for a tools.jar in various places.
     *
     * @param pClassName class name to lookup
     * @return the found class
     * @throws ClassNotFoundException if no class could be found
     */
    public static Class lookupClass(String pClassName) throws ClassNotFoundException {
        try {
            return Class.forName(pClassName);
        } catch (ClassNotFoundException exp) {
            return lookupInToolsJar(pClassName);
        }
    }

    /**
     * Searches for <code>tools.jar</code> in various locations and uses an {@link URLClassLoader} for
     * loading a class from this files. If the class could not be found in any, then a {@link ClassNotFoundException}
     * is thrown. The locations used for lookup are (in this order)
     *
     * <ul>
     *     <li>$JAVA_HOME/../lib/tools.jar</li>
     *     <li>$JAVA_HOME/lib/tools.jar</li>
     * </ul>
     *
     * <code>$JAVA_HOME</code> here is the value of the system property <code>java.home</code>
     *
     * @param pClassName class to lookup
     * @return the found class
     * @throws ClassNotFoundException if no class could be found
     */
    public static Class lookupInToolsJar(String pClassName) throws ClassNotFoundException {
        // Try to look up tools.jar within $java.home, otherwise give up
        String extraInfo;
        if (JAVA_HOME != null) {
            extraInfo = "JAVA_HOME is " + JAVA_HOME;
            for (File toolsJar : TOOLS_JAR_LOCATIONS) {
                try {
                    if (toolsJar.exists()) {
                        ClassLoader loader = createClassLoader(toolsJar);
                        return loader.loadClass(pClassName);
                    }
                } catch (MalformedURLException e) {
                    // Cannot happen because the URL comes from a File.
                    // And if, we throws an class not found exception.
                    extraInfo = "Cannot create URL from " + toolsJar;
                }
            }
        } else {
            extraInfo = "No JAVA_HOME set";
        }
        throw new ClassNotFoundException("No tools.jar found (" + extraInfo + ")");
    }


    // Create a classloader and respect a security manager if installed
    private static ClassLoader createClassLoader(File toolsJar) throws MalformedURLException {
        final URL urls[] = new URL[] {toolsJar.toURI().toURL() };
        if (System.getSecurityManager() == null) {
            return new URLClassLoader(urls, getParentClassLoader());
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                /** {@inheritDoc} */
                public ClassLoader run() {
                    return new URLClassLoader(urls, getParentClassLoader());
                }
            });
        }
    }

    private static ClassLoader getParentClassLoader() {
        ClassLoader loader = ToolsClassFinder.class.getClassLoader();
        return loader == null ? ClassLoader.getSystemClassLoader() : loader;
    }
}
