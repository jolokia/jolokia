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

package org.jolokia.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URL;
import java.util.*;

/**
 * @author roland
 * @since 05.11.10
 */
public class ServiceObjectFactory {

    private ServiceObjectFactory() {}

    public static <T> List<T> createServiceObjects(String... pDescriptorPaths) {
        Map<String,T> extractorMap = new HashMap<String,T>();
        List<T> extractors = new LinkedList<T>();

        for (String descriptor : pDescriptorPaths) {
            readServiceDefinitions(extractorMap, extractors, descriptor);
        }
        return extractors;
    }

    private static <T> void readServiceDefinitions(Map<String, T> pExtractorMap, List<T> pExtractors, String pDefPath) {
        try {
            Enumeration<URL> resUrls = ServiceObjectFactory.class.getClassLoader().getResources(pDefPath);
            while (resUrls.hasMoreElements()) {
                readServiceDefinitionFromUrl(pExtractorMap, pExtractors, resUrls.nextElement());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load extractor from " + pDefPath + ": " + e,e);
        }
    }

    private static <T> void readServiceDefinitionFromUrl(Map<String, T> pExtractorMap,
                                                         List<T> pExtractors, URL pUrl) {
        String line = null;
        Exception error = null;
        LineNumberReader reader = null;
        try {
            reader = new LineNumberReader(new InputStreamReader(pUrl.openStream()));
            line = reader.readLine();
            while (line != null) {
                createOrRemoveService(pExtractorMap, pExtractors, line);
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
                throw new IllegalStateException("Cannot load extractor " + line + " defined in " +
                        pUrl + " : " + error + ". Aborting",error);
            }
        }
    }
    private static <T> void createOrRemoveService(Map<String, T> pExtractorMap, List<T> pExtractors, String pLine)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        if (pLine.length() > 0) {
            if (pLine.startsWith("!")) {
                T ext = pExtractorMap.remove(pLine.substring(1));
                if (ext != null) {
                    pExtractors.remove(ext);
                }
            } else {
                Class<T> clazz = (Class<T>) ServiceObjectFactory.class.getClassLoader().loadClass(pLine);
                T ext = (T) clazz.newInstance();
                pExtractorMap.put(pLine,ext);
                pExtractors.add(ext);
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

}
