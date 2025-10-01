/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utils to handle Jolokia escaping. Copy of {@code org.jolokia.server.core.util.EscapeUtil} until
 * we extract these to top-level {@code jolokia-conversion} module.
 */
public class EscapeUtil {

    /**
     * {@link Pattern} to split a string by {@code /}, but not if there's {@code !/}. So {@code abc/def} is split
     * into {@code abc} and {@def}, but {@code abc!/def} is not split at all.
     */
    private static final Pattern SLASH_ESCAPE_PATTERN = Pattern.compile("((?:[^!/]|!.)*)(?:/|$)");

    /**
     * {@link Pattern} to match any escaped character (Jolokia uses {@code !} as escape character.
     */
    private static final Pattern UNESCAPE_PATTERN = Pattern.compile("!(.)");

    /**
     * Split up a path taking into account proper escaping (as described in the
     * <a href="http://www.jolokia.org/reference">reference manual</a>). So we don't simply split by {@code /} when
     * the slash character is escaped with exclamation mark.
     *
     * @param pArg string to split with escaping taken into account
     * @return split element or null if the argument was null.
     */
    public static List<String> splitPath(String pArg) {
        List<String> ret = new ArrayList<>();
        if (pArg != null) {
            Matcher m = SLASH_ESCAPE_PATTERN.matcher(pArg);
            while (m.find() && m.start(1) != pArg.length()) {
                ret.add(UNESCAPE_PATTERN.matcher(m.group(1)).replaceAll("$1"));
            }
        }
        return ret;
    }

    /**
     * Combine a list of path segments by escaping the segments and joining with {@code /}.
     *
     * @param pathElements
     * @return
     */
    public static String combinePath(List<String> pathElements) {
        if (pathElements != null && !pathElements.isEmpty()) {
            StringBuilder path = new StringBuilder();
            for (int i = 0; i < pathElements.size(); i++) {
                if (pathElements.get(i) == null) {
                    continue;
                }
                path.append(EscapeUtil.escape(pathElements.get(i)));
                if (i < pathElements.size() - 1) {
                    path.append("/");
                }
            }
            return path.toString();
        }
        return null;
    }

    /**
     * Escape a input (like the part of an path) so that it can be safely used
     * e.g. as a path using <em>Jolokia encoding rules</em>. Only two characters are escaped with {@code !} - slash
     * {@code /} and exclamation mark {@code !}.
     *
     * @param pValue value to escape
     * @return the escaped value, so it can be used as "single" URL path segment
     */
    public static String escape(String pValue) {
        return pValue.replaceAll("!", "!!").replaceAll("/", "!/");
    }

}
