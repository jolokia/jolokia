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

package org.jolokia.core.util;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.*;

/**
 * A simple factory for creating services with no-arg constructors from a textual
 * descriptor. This descriptor, which must be a resource loadable by this class'
 * classloader, is a plain text file which looks like
 *
 * <pre>
 *   org.jolokia.service.jmx.detector.TomcatDetector,50
 *   !org.jolokia.service.jmx.detector.JettyDetector
 *   org.jolokia.service.jmx.detector.JBossDetector
 *   org.jolokia.service.jmx.detector.WebsphereDetector,1500
 * </pre>
 *
 * If a line starts with <code>!</code> it is removed if it has been added previously.
 * The optional second numeric value is the order in which the services are returned.
 *
 * The services to create can have either a no-op constructor or one with an integer
 * argument. If a constructor with an integer value exists than it is fed with
 * the determined order. This feature is typically used in combination with
 * {@link Comparable} to obtain a {@link SortedSet} which can merged with other sets
 * and keeping the order intact. To get the services as set, use {@link #createServicesAsSet(String...)}
 *
 * @author roland
 * @since 05.11.10
 */
public final class LocalServiceFactory {

    private LocalServiceFactory() {}

    /**
     * Create a list of services ordered according to the ordering given in the
     * service descriptor files. Note, that the descriptor will be looked up
     * in the whole classpath space, which can result in reading in multiple
     * descriptors with a single path. Note, that the reading order for multiple
     * resources with the same  name is not defined.
     *
     * @param pDescriptorPaths a list of resource paths which are handle in the given order.
     *        Normally, default service should be given as first parameter so that custom
     *        descriptors have a chance to remove a default service.
     * @param <T> type of the service objects to create
     * @return a ordered list of created services.
     */
    public static <T> List<T> createServices(String... pDescriptorPaths) {
        try {
            ServiceEntry.initDefaultOrder();
            TreeMap<ServiceEntry,T> extractorMap = new TreeMap<ServiceEntry,T>();
            for (String descriptor : pDescriptorPaths) {
                readServiceDefinitions(extractorMap, descriptor);
            }
            List<T> ret = new ArrayList<T>();
            for (T service : extractorMap.values()) {
                ret.add(service);
            }
            return ret;
        } finally {
            ServiceEntry.removeDefaultOrder();
        }
    }


    /**
     * Create services as a set. This set is sorted and hence the created services should
     * implement {@link Comparable}.
     *
     * @param pDescriptorPaths a list of resource paths which are handle in the given order.
     *        Normally, default service should be given as first parameter so that custom
     *        descriptors have a chance to remove a default service.
     * @param <T> type of the service objects to create
     * @return a sorted list of created services.
     */
    public static <T> SortedSet<T> createServicesAsSet(String... pDescriptorPaths) {
        return new TreeSet<T>((List<T>) createServices(pDescriptorPaths));
    }

    // ==================================================================================

    private static <T> void readServiceDefinitions(Map <ServiceEntry, T> pExtractorMap, String pDefPath) {
        try {
            Enumeration<URL> resUrls = LocalServiceFactory.class.getClassLoader().getResources(pDefPath);
            while (resUrls.hasMoreElements()) {
                readServiceDefinitionFromUrl(pExtractorMap, resUrls.nextElement());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load extractor from " + pDefPath + ": " + e,e);
        }
    }

    private static <T> void readServiceDefinitionFromUrl(Map<ServiceEntry, T> pExtractorMap,URL pUrl) {
        String line = null;
        Exception error = null;
        LineNumberReader reader = null;
        try {
            reader = new LineNumberReader(new InputStreamReader(pUrl.openStream()));
            while ( (line = reader.readLine()) != null) {
                // Skip empty lines and comments
                if (line.trim().length() > 0 && !line.matches("^\\s*#.*$")) {
                    createOrRemoveService(pExtractorMap, line);
                }
            }
        } catch (Exception e) {
            error = e;
        } finally {
            closeReader(reader);
            if (error != null) {
                throw new IllegalStateException("Cannot load service " + line + " defined in " +
                        pUrl + " : " + error + ". Aborting",error);
            }
        }
    }

    private static <T> void createOrRemoveService(Map<ServiceEntry, T> pExtractorMap, String pLine)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        if (pLine.length() > 0) {
            ServiceEntry entry = new ServiceEntry(pLine);
            if (entry.isRemove()) {
                // Removing is a bit complex since we need to find out
                // the proper key since the order is part of equals/hash
                // so we cant fetch/remove it directly
                Set<ServiceEntry> toRemove = new HashSet<ServiceEntry>();
                for (ServiceEntry key : pExtractorMap.keySet()) {
                    if (key.getClassName().equals(entry.getClassName())) {
                        toRemove.add(key);
                    }
                }
                for (ServiceEntry key : toRemove) {
                    pExtractorMap.remove(key);
                }
            } else {
                // Create a new object. If an constructor with a single int
                // argument is given, this constructor is used and feed with
                // the order. This is typically used in combination with implementing
                // and {@link Comparable} interface to get a sorted set
                Class<T> clazz = (Class<T>) LocalServiceFactory.class.getClassLoader().loadClass(entry.getClassName());
                T ext;
                try {
                    Constructor ctr = clazz.getConstructor(int.class);
                    pExtractorMap.put(entry, (T) ctr.newInstance(entry.getOrder()));
                } catch (NoSuchMethodException e) {
                    ext = clazz.newInstance();
                    pExtractorMap.put(entry,ext);
                } catch (InvocationTargetException e) {
                    throw new IllegalArgumentException("Can not instantiate " + entry.getClassName() + ": " + e,e);
                }
            }
        }
    }

    private static void closeReader(LineNumberReader pReader) {
        if (pReader != null) {
            try {
                pReader.close();
            } catch (IOException e) {
                // Best effort
            }
        }
    }

    // =============================================================================

    static class ServiceEntry implements Comparable<ServiceEntry> {
        private String className;
        private boolean remove;
        private int order;

        // Thread holding the current default orders
        private static ThreadLocal<Integer> defaultOrderHolder = new ThreadLocal<Integer>();

        // Thread local holding an old order which gets restored on remove(). This is required
        // for nested service lookups
        private static ThreadLocal<Stack<Integer>> defaultOrders = new ThreadLocal<Stack<Integer>>();

        /**
         * Parse an entry in the service definition. This should be the full qualified classname
         * of a service, optional prefixed with "<code>!</code>" in which case the service is removed
         * from the defaul list. An order value can be appened after the classname with a comma for give a
         * indication for the ordering of services. If not given, 100 is taken for the first entry, counting up.
         *
         * @param pLine line to parse
         */
        public ServiceEntry(String pLine) {
            String[] parts = pLine.split(",");
            if (parts[0].startsWith("!")) {
                remove = true;
                className = parts[0].substring(1);
            } else {
                remove = false;
                className = parts[0];
            }
            if (parts.length > 1) {
                try {
                    order = Integer.parseInt(parts[1]);
                } catch (NumberFormatException exp) {
                    order = nextDefaultOrder();
                }
            } else {
                order = nextDefaultOrder();
            }
        }

         int getOrder() {
             return order;
         }

         private Integer nextDefaultOrder() {
            Integer defaultOrder = defaultOrderHolder.get();
            defaultOrderHolder.set(defaultOrder + 1);
            return defaultOrder;
        }

        private static void initDefaultOrder() {
            Integer old = defaultOrderHolder.get();
            Stack<Integer> orderStack = defaultOrders.get();
            if (orderStack == null) {
                orderStack = new Stack<Integer>();
                defaultOrders.set(orderStack);
            }
            if (old != null) {
                orderStack.push(old);
            }
            defaultOrderHolder.set(100);
        }

        private static void removeDefaultOrder() {
            Stack<Integer> orderStack = defaultOrders.get();
            if (orderStack == null) {
                throw new IllegalStateException("No initDefaultOrder() called before");
            }
            if (orderStack.size() > 0) {
                defaultOrderHolder.set(orderStack.pop());
            } else {
                defaultOrderHolder.remove();
                defaultOrders.remove();
            }
        }

        private String getClassName() {
            return className;
        }


        private boolean isRemove() {
            return remove;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) { return true; }
            if (o == null || getClass() != o.getClass()) { return false; }

            ServiceEntry that = (ServiceEntry) o;

            return className.equals(that.className);
        }

        @Override
        public int hashCode() {
            return className.hashCode();
        }

        /** {@inheritDoc} */
        public int compareTo(ServiceEntry o) {
            int ret = order - o.order;
            return ret != 0 ? ret : className.compareTo(o.className);
        }
    }
}
