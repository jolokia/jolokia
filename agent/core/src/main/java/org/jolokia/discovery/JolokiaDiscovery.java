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
package org.jolokia.discovery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A little helper class so that we can broadcast the details of jolokia agents so its easier to
 * auto-discover them on a machine, such as via files or zeroconf etc
 */
public class JolokiaDiscovery {
    protected static JolokiaDiscovery instance = new JolokiaDiscovery();
    private List<DiscoveryListener> listeners = new CopyOnWriteArrayList<DiscoveryListener>();
    private AtomicBoolean loaded = new AtomicBoolean(false);

    /**
     * Returns the single instance
     */
    public static JolokiaDiscovery getInstance() {
        return instance;
    }

    /**
     * Invoked when an agent is started on a given URL
     */
    public void agentStarted(AgentDetails details) {
        onStartupFindClassPathDiscoveryAgents();
        for (DiscoveryListener listener : listeners) {
            listener.onAgentStarted(details);
        }
    }

    /**
     * Invoked when an agent is stopped on a given URL
     */
    public void agentStopped(AgentDetails details) {
        onStartupFindClassPathDiscoveryAgents();
        for (DiscoveryListener listener : listeners) {
            listener.onAgentStopped(details);
        }
    }

    public void addListener(DiscoveryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(DiscoveryListener listener) {
        listeners.remove(listener);
    }

    /**
     * Lets attempt to dynamically load a discovery agent if one is available on the classpath
     */
    protected void onStartupFindClassPathDiscoveryAgents() {
        if (loaded.compareAndSet(false, true)) {
            Set<String> classNames = new HashSet<String>();

            loadDiscoveryAgentClassNames(classNames, Thread.currentThread().getContextClassLoader());
            loadDiscoveryAgentClassNames(classNames, getClass().getClassLoader());


            for (String className : classNames) {
                Class clazz = loadClass(className);
                if (clazz != null) {
                    Object instance = null;
                    try {
                        instance = clazz.newInstance();
                    } catch (Exception e) {
                        System.out.println("Failed to instantiate class: " + clazz + ". " + e);
                        e.printStackTrace();
                    }
                    if (instance instanceof DiscoveryListener) {
                        addListener((DiscoveryListener) instance);
                    }
                }
            }
        }
    }

    protected void loadDiscoveryAgentClassNames(Set<String> classNames, ClassLoader classLoader) {
        String path = "META-INF/services/org/jolokia/discovery/DiscoveryListener";
        try {
            Enumeration<URL> iter = classLoader.getResources(path);
            while (iter.hasMoreElements()) {
                URL url = iter.nextElement();
                if (url != null) {
                    InputStream in = url.openStream();
                    if (in != null) {
                        try {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                            while (true) {
                                String line = reader.readLine();
                                if (line != null) {
                                    line = line.trim();
                                    if (!line.startsWith("#") || line.length() > 0) {
                                        classNames.add(line);
                                    }
                                } else {
                                    break;
                                }
                            }
                        } finally {
                            try {
                                in.close();
                            } catch (Exception e) {
                                // ignore
                            }
                        }

                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to discover services at " + path + ". " + e);
        }
    }

    protected Class loadClass(String className) {
        if (className != null && className.length() > 0) {
            try {
                return Class.forName(className);
            } catch (Throwable e) {
                try {
                    return Thread.currentThread().getContextClassLoader().loadClass(className);
                } catch (Throwable e1) {
                    try {
                        return getClass().getClassLoader().loadClass(className);
                    } catch (Throwable e2) {
                        System.out.println("Failed to load class '" + className + "' on the classpath");
                    }
                }
            }
        }
        return null;
    }
}
