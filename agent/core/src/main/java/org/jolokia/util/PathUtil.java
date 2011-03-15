/*
 * Copyright 2011 Roland Huss
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

import java.util.*;

/**
 * Utility class for handling request pathes.
 *
 * @author roland
 * @since 15.03.11
 */
public class PathUtil {

    private PathUtil() {}

    /**
     * Combine a list of strings to a single path with proper escaping.
     *
     * @param pParts parts to combine
     * @return the combined path
     */
    public static String combineToPath(List<String> pParts) {
        if (pParts != null && pParts.size() > 0) {
            StringBuffer buf = new StringBuffer();
            Iterator<String> it = pParts.iterator();
            while (it.hasNext()) {
                buf.append(escapePathPart(it.next()));
                if (it.hasNext()) {
                    buf.append("/");
                }
            }
            return buf.toString();
        } else {
            return null;
        }
    }

    /**
     * Parse a string path and return a list of split up parts.
     *
     * @param pPath the path to parse. Can be null
     * @return list of path elements or null if the initial path is null.
     */
    public static List<String> parsePath(String pPath) {
        if (pPath != null) {
            // Split on '/' but not on '\/':
            String[] elements = pPath.split("(?<!\\\\)/+");
            List<String> ret = new ArrayList<String>();
            for (String element : elements) {
                ret.add(element.replaceAll("\\\\/", "/"));
            }
            return ret;
        } else {
            return null;
        }
    }

    /**
     * Escape a single path part so that slashes are escaped with a backslash
     *
     * @param pPathPart path part to escape.
     * @return return the escaped part
     */
    public static String escapePathPart(String pPathPart) {
        return pPathPart.replaceAll("/","\\\\/");
    }

}
