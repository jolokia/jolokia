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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling escaping of strings and paths.
 *
 * @author roland
 * @since 15.03.11
 */
public final class EscapeUtil {

    /**
     * Escape character used for path escaping as it can be used
     * in a regexp
     */
    public static final String PATH_ESCAPE = "!";

    /**
     * Escape character for escaping CSV type string as it can be used in a
     * regexp. E.g. a backslash (\ or "\\") must be doubled (\\ or "\\\\")
     */
    public static final String CSV_ESCAPE = "\\\\";
    
    // Compile patterns in advance and cache them
    static final Map<String,Pattern[]> SPLIT_PATTERNS = new HashMap<String, Pattern[]>();
    static {
        for (String param[] : new String[][] {
                { PATH_ESCAPE, "/"} ,
                { CSV_ESCAPE, ","},
                { CSV_ESCAPE, "="}
        }) {
            String esc = param[0];
            String del = param[1];
            SPLIT_PATTERNS.put(esc + del, createSplitPatterns(esc, del));
        }
    }

    private EscapeUtil() {}

    /**
     * Combine a list of strings to a single path with proper escaping.
     *
     * @param pParts parts to combine
     * @return the combined path
     */
    public static String combineToPath(List<String> pParts) {
        if (pParts != null && pParts.size() > 0) {
            StringBuilder buf = new StringBuilder();
            Iterator<String> it = pParts.iterator();
            while (it.hasNext()) {
                String part = it.next();
                buf.append(escapePart(part != null ? part : "*"));
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
        // Special cases which simply implies 'no path'
        if (pPath == null || pPath.equals("") || pPath.equals("/")) {
            return null;
        }
        return replaceWildcardsWithNull(split(pPath, PATH_ESCAPE, "/"));
    }

    /**
     * Get the path as a reverse stack with the first element of the path on top
     *
     * @param pPath path to parse
     * @return stack of arguments in reverse order or an empty stack if path was null or empty
     */
    public static Stack<String> extractElementsFromPath(String pPath) {
        return reversePath(parsePath(pPath));
    }

    /**
     * Reverse path and return as a stack. First path element is on top
     * of the stack.
     *
     * @param pathParts path to reverse
     * @return reversed path or an empty stack if no path parts are given. Never return null.
     */
    public static Stack<String> reversePath(List<String> pathParts) {
        Stack<String> pathStack = new Stack<String>();
        if (pathParts != null) {
            // Needs first extra argument at top of the stack
            for (int i = pathParts.size() - 1;i >=0;i--) {
                pathStack.push(pathParts.get(i));
            }
        }
        return pathStack;
    }

    /**
     * Split a string on a delimiter, respecting escaping with an escape char. Assuming
     * that a backslash (<code>\</code>) is used as escape char, then the following
     * replacement rules apply:
     *
     * <ul>
     *  <li>
     *    <code>\</code><em>delimiter</em> for the delimiter as literal
     *  </li>
     *  <li>
     *    <code>\\</code> for backslashes
     *  </li>
     *  <li>
     *   <code>\</code><em>(everything else)</em> is the same as <em>(everything else)</em>.
     *  </li>
     *
     * @param pArg argument to split
     * @param pEscape escape pattern as it can be used in a regular expression.
     * @param pDelimiter delimiter to use
     * @return the split string as list or an empty array if the argument was null
     */
    public static List<String> split(String pArg, String pEscape, String pDelimiter) {
        if (pArg != null) {
            ArrayList<String> ret = new ArrayList<String>();
            Pattern[] pattern = SPLIT_PATTERNS.get(pEscape + pDelimiter);
            if (pattern == null) {
                pattern = createSplitPatterns(pEscape, pDelimiter);
                SPLIT_PATTERNS.put(pEscape + pDelimiter,pattern);
            }

            final Matcher m = pattern[0].matcher(pArg);
            while (m.find() && m.start(1) != pArg.length()) {
                // Finally unescape all escaped parts. Trailing escapes are captured before the delimiter applies
                String trailingEscapes = m.group(2);
                ret.add(pattern[1].matcher(m.group(1) + (trailingEscapes != null ? trailingEscapes : "")).replaceAll("$1"));
            }
            return ret;
        } else {
            return null;
        }
    }

    /**
     * Split but return an array which is never null (but might be empty)
     *
     * @param pArg argument to split
     * @param pEscape single character used for escaping
     * @param pDelimiter delimiter to use
     * @return the splitted string as list or an empty array if the argument was null
     */
    public static String[] splitAsArray(String pArg, String pEscape, String pDelimiter) {
        if (pArg != null) {
            List<String> elements = split(pArg, pEscape, pDelimiter);
            return elements.toArray(new String[elements.size()]);
        } else {
            return new String[0];
        }
    }

    /**
     * Escape the delimiter in an argument with the given escape char.
     *
     * @param pArg string to add escapes to
     * @param pEscape the escape character (e.g. '\')
     * @param pDelimiter the delimiter to escape (e.g. ',')
     * @return the escaped char
     */
    public static String escape(String pArg, String pEscape, String pDelimiter) {
        return pArg.replaceAll(pEscape, pEscape + pEscape).replaceAll(pDelimiter, pEscape + pDelimiter);
    }

    // ===================================================================================

    // Create a split pattern for a given delimiter
    private static Pattern[] createSplitPatterns(String pEscape, String pDel) {
        return new Pattern[] {
                // Escape ($1: Everything before the delimiter, $2: Trailing escaped values (optional)
                Pattern.compile("(.*?)" + // Any chars
                                "(?:" +
                                   // The delimiter not preceded by an escape (but pairs of escape & value can be in
                                   // are allowed before nevertheless). A negative-look-before (?<!) is used for this
                                   // purpose
                                   "(?<!" + pEscape  + ")((?:" + pEscape + ".)*)" + pDel + "|" +
                                   "$" +    // or end-of-line
                                 ")",Pattern.DOTALL),

                // Unescape, group must match unescaped value
                Pattern.compile(pEscape + "(.)",Pattern.DOTALL)
        };
    }

    // Escape a single part
    private static final Pattern ESCAPE_PATTERN = Pattern.compile(PATH_ESCAPE);

    private static final Pattern SLASH_PATTERN = Pattern.compile("/");
    private static String escapePart(String pPart) {
        return SLASH_PATTERN.matcher(
                ESCAPE_PATTERN.matcher(pPart).replaceAll(PATH_ESCAPE + PATH_ESCAPE)).replaceAll(PATH_ESCAPE + "/");
    }

    private static List<String> replaceWildcardsWithNull(List<String> pParts) {
        if (pParts  == null) {
            return null;
        }
        List<String> ret = new ArrayList<String>(pParts.size());
        for (String part : pParts) {
            ret.add("*".equals(part) ? null : part);
        }
        return ret;
    }
}
