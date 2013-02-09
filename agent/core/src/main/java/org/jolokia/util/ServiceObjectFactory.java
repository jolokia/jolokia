package org.jolokia.util;

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
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URL;
import java.util.*;

/**
 * A simple factory for creating services with no-arg constructors from a textual
 * descriptor. This descriptor, which must be a resource loadable by this class'
 * classloader, is a plain text file which looks like
 *
 * <pre>
 *   org.jolokia.detector.TomcatDetector,50
 *   !org.jolokia.detector.JettyDetector
 *   org.jolokia.detector.JBossDetector
 *   org.jolokia.detector.WebsphereDetector,1500
 * </pre>
 *
 * If a line starts with <code>!</code> it is removed if it has been added previously.
 * The optional second numeric value is the order in which the services are returned.
 *
 * @author roland
 * @since 05.11.10
 */
public final class ServiceObjectFactory {

    private ServiceObjectFactory() {}

    /**
     * Create a list of services ordered according to the ordering given in the
     * service descriptor files. Note, that the descriptor will be looked up
     * in the whole classpath space, which can result in reading in multiple
     * descriptors with a single path. Note, that the reading order for multiple
     * resources with the same name is not defined.
     *
     * @param pDescriptorPaths a list of resource paths which are handle in the given order.
     *        Normally, default service should be given as first parameter so that custom
     *        descriptors have a chance to remove a default service.
     * @param <T> type of the service objects to create
     * @return a ordered list of created services.
     */
    public static <T> List<T> createServiceObjects(String... pDescriptorPaths) {
        try {
            ServiceEntry.initDefaultOrder();
            TreeMap<ServiceEntry,T> extractorMap = new TreeMap<ServiceEntry,T>();
            for (String descriptor : pDescriptorPaths) {
                readServiceDefinitions(extractorMap, descriptor);
            }
            ArrayList<T> ret = new ArrayList<T>();
            for (T service : extractorMap.values()) {
                ret.add(service);
            }
            return ret;
        } finally {
            ServiceEntry.removeDefaultOrder();
        }
    }

    private static <T> void readServiceDefinitions(Map<ServiceEntry, T> pExtractorMap, String pDefPath) {
        try {
            Enumeration<URL> resUrls = ServiceObjectFactory.class.getClassLoader().getResources(pDefPath);
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
            line = reader.readLine();
            while (line != null) {
                createOrRemoveService(pExtractorMap, line);
                line = reader.readLine();
            }
        } catch (ClassNotFoundException e) {
            error = e;
        } catch (InstantiationException e) {
            error = e;
        } catch (IllegalAccessException e) {
            error = e;
        } catch (ClassCastException e) {
            error = e;
        } catch (IOException e) {
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
                Class<T> clazz = (Class<T>) ServiceObjectFactory.class.getClassLoader().loadClass(entry.getClassName());
                T ext = (T) clazz.newInstance();
                pExtractorMap.put(entry,ext);
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
        private Integer order;

        private static ThreadLocal<Integer> defaultOrderHolder = new ThreadLocal<Integer>() {

            /**
             * Initialise with start value for entries without an explicite order. 100 in this case.
             *
             * @return 100
             */
            @Override
            protected Integer initialValue() {
                return Integer.valueOf(100);
            }
        };

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

        private Integer nextDefaultOrder() {
            Integer defaultOrder = defaultOrderHolder.get();
            defaultOrderHolder.set(defaultOrder + 1);
            return defaultOrder;
        }

        private static void initDefaultOrder() {
            defaultOrderHolder.set(100);
        }

        private static void removeDefaultOrder() {
            defaultOrderHolder.remove();
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

            if (!className.equals(that.className)) { return false; }

            return true;
        }

        @Override
        public int hashCode() {
            return className.hashCode();
        }

        /** {@inheritDoc} */
        public int compareTo(ServiceEntry o) {
            return order - o.order;
        }
    }
}
