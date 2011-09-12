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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling request pathes.
 *
 * @author roland
 * @since 15.03.11
 */
public final class PathUtil {

    /**
     * Pattern for detecting escaped slashes in URL encoded requests
     */
    public static final Pattern SLASH_ESCAPE_PATTERN = Pattern.compile("^\\^?-*\\+?$");

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

    /*
      */

    /**
     * Extract element from an URL path and put them on a stack. Slashes are escaped with a special algorithm
     * <p>
     * We need to use this special treating for slashes (i.e. to escape with '/-/') because URI encoding doesnt work
     * well with HttpRequest.pathInfo() since in Tomcat/JBoss slash seems to be decoded to early so that it get messed up
     * and answers with a "HTTP/1.x 400 Invalid URI: noSlash" without returning any further indications
     * <p>
     * For the rest of unsafe chars, we use uri decoding (as anybody should do). It could be of course the case,
     * that the pathinfo has been already uri decoded (dont know by heart).
     *
     * @param pPath path to split
     * @return stack with splitted path
     */
    public static Stack<String> extractElementsFromPath(String pPath) {
        // Strip leadings slahes
        String cleanPath = pPath.replaceFirst("^/+", "");
        String[] elements = cleanPath.split("/+");

        Stack<String> ret = new Stack<String>();
        Stack<String> elementStack = new Stack<String>();

        for (int i=elements.length-1;i>=0;i--) {
            elementStack.push(elements[i]);
        }

        extractElements(ret,elementStack,null);

        // Reverse stack
        Collections.reverse(ret);

        return ret;
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

    private static void unescapeSlashes(String pCurrentElement, Stack<String> pRet,
                                       Stack<String> pElementStack, StringBuffer pPreviousBuffer)  {
        if (pRet.isEmpty()) {
            return;
        }
        StringBuffer val;

        // Special escape at the beginning indicates that this element belongs
        // to the next one
        if (pCurrentElement.substring(0,1).equals("^")) {
            val = new StringBuffer();
        } else if (pPreviousBuffer == null) {
            val = new StringBuffer(pRet.pop());
        } else {
            val = pPreviousBuffer;
        }
        // Append appropriate nr of slashes
        expandSlashes(val, pCurrentElement);

        // Special escape at the end indicates that this is the last element in the path
        if (!pCurrentElement.substring(pCurrentElement.length()-1, pCurrentElement.length()).equals("+")) {
            if (!pElementStack.isEmpty()) {
                val.append(decode(pElementStack.pop()));
            }
            extractElements(pRet,pElementStack,val);
        } else {
            pRet.push(decode(val.toString()));
            extractElements(pRet,pElementStack,null);
        }
    }

    private static void extractElements(Stack<String> pRet, Stack<String> pElementStack, StringBuffer pPreviousBuffer) {
        if (pElementStack.isEmpty()) {
            if (pPreviousBuffer != null && pPreviousBuffer.length() > 0) {
                pRet.push(decode(pPreviousBuffer.toString()));
            }
            return;
        }
        String element = pElementStack.pop();
        Matcher matcher = SLASH_ESCAPE_PATTERN.matcher(element);
        if (matcher.matches()) {
            unescapeSlashes(element, pRet, pElementStack, pPreviousBuffer);
        } else {
            if (pPreviousBuffer != null) {
                pRet.push(decode(pPreviousBuffer.toString()));
            }
            pRet.push(decode(element));
            extractElements(pRet,pElementStack,null);
        }
    }

    private static void expandSlashes(StringBuffer pVal, String pElement) {
        for (int j=0;j< pElement.length();j++) {
            pVal.append("/");
        }
    }

    private static String decode(String s) {
        return s;
        //return URLDecoder.decode(s,"UTF-8");
    }
}
