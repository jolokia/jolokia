package org.jolokia.request;

import java.util.*;

import javax.management.MalformedObjectNameException;

import org.jolokia.config.ProcessingParameters;
import org.jolokia.util.EscapeUtil;
import org.jolokia.util.RequestType;

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


/**
 * Factory for creating {@link JmxRequest}s
 *
 * @author roland
 * @since Oct 29, 2009
 */
public final class JmxRequestFactory {

    // private constructor for static class
    private JmxRequestFactory() { }

    /**
     *
     * Create a JMX request from a GET Request with a REST Url.
     * <p>
     * The REST-Url which gets recognized has the following format:
     * <p>
     * <pre>
     *    &lt;base_url&gt;/&lt;type&gt;/&lt;param1&gt;/&lt;param2&gt;/....
     * </pre>
     * <p>
     * where <code>base_url<code> is the URL specifying the overall servlet (including
     * the servlet context, something like "http://localhost:8080/j4p-agent"),
     * <code>type</code> the operational mode and <code>param1 .. paramN<code>
     * the provided parameters which are dependend on the <code>type<code>
     * <p>
     * The following types are recognized so far, along with there parameters:
     *
     * <ul>
     *   <li>Type: <b>read</b> ({@link RequestType#READ}<br/>
     *       Parameters: <code>param1<code> = MBean name, <code>param2</code> = Attribute name,
     *       <code>param3 ... paramN</code> = Inner Path.
     *       The inner path is optional and specifies a path into complex MBean attributes
     *       like collections or maps. If within collections/arrays/tabular data,
     *       <code>paramX</code> should specify
     *       a numeric index, in maps/composite data <code>paramX</code> is a used as a string
     *       key.</li>. If the attribute name contains "," it is interpreted as a list of attributes.
     *       which should be returned.
     *   <li>Type: <b>write</b> ({@link RequestType#WRITE}<br/>
     *       Parameters: <code>param1</code> = MBean name, <code>param2</code> = Attribute name,
     *       <code>param3</code> = value, <code>param4 ... paramN</code> = Inner Path.
     *       The value must be URL encoded (with UTF-8 as charset), and must be convertible into
     *       a data structure</li>
     *   <li>Type: <b>exec</b> ({@link RequestType#EXEC}<br/>
     *       Parameters: <code>param1</code> = MBean name, <code>param2</code> = operation name,
     *       <code>param4 ... paramN</code> = arguments for the operation.
     *       The arguments must be URL encoded (with UTF-8 as charset), and must be convertable into
     *       a data structure</li>
     *    <li>Type: <b>version</b> ({@link RequestType#VERSION}<br/>
     *        Parameters: none
     *    <li>Type: <b>search</b> ({@link RequestType#SEARCH}<br/>
     *        Parameters: <code>param1</code> = MBean name pattern
     * </ul>
     * @param pPathInfo path info of HTTP request
     * @param pProcessingParameters processing parameters. Must not be null/
     * @return a newly created {@link JmxRequest}
     */
    public static <R extends JmxRequest> R createGetRequest(String pPathInfo, ProcessingParameters pProcessingParameters) {
        RequestType type = null;
        try {
            String pathInfo = extractPathInfo(pPathInfo, pProcessingParameters);

            // Get all path elements as a reverse stack
            Stack<String> elements = EscapeUtil.extractElementsFromPath(pathInfo);

            // Use version by default if no type is given
            type = elements.size() != 0 ? RequestType.getTypeByName(elements.pop()) : RequestType.VERSION;

            // Parse request
            return (R) getCreator(type).create(elements, pProcessingParameters);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid object name. " + e.getMessage(),e);
        } catch (EmptyStackException exp) {
            throw new IllegalArgumentException("Invalid arguments in pathinfo " + pPathInfo + (type != null ? " for command " + type : ""),exp);
        }
    }


    /**
     * Create a single {@link JmxRequest}s from a JSON map representation of a request
     *
     * @param pRequestMap JSON representation of a {@link JmxRequest}
     * @param pProcessingParams additional map of operational parameters. Must not be null.
     * @return the created {@link JmxRequest}
     */
    public static <R extends JmxRequest> R createPostRequest(Map<String, ?> pRequestMap, ProcessingParameters pProcessingParams) {
        try {
            ProcessingParameters paramsMerged = pProcessingParams.mergedParams((Map<String,String>) pRequestMap.get("config"));
            RequestType type = RequestType.getTypeByName((String) pRequestMap.get("type"));
            return (R) getCreator(type).create(pRequestMap, paramsMerged);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid object name. " + e.getMessage(),e);
        }
    }

    /**
     * Create a list of {@link JmxRequest}s from a JSON list representing jmx requests
     *
     * @param pJsonRequests JSON representation of a list of {@link JmxRequest}
     * @param pProcessingParams processing options. Must not be null.
     * @return list with one or more {@link JmxRequest}
     */
    public static List<JmxRequest> createPostRequests(List pJsonRequests, ProcessingParameters pProcessingParams) {
        List<JmxRequest> ret = new ArrayList<JmxRequest>();
        for (Object o : pJsonRequests) {
            if (!(o instanceof Map)) {
                throw new IllegalArgumentException("Not a request within the list of requests " + pJsonRequests +
                        ". Expected map, but found: " + o);
            }
            ret.add(createPostRequest((Map<String,?>) o,pProcessingParams));
        }
        return ret;
    }

    // ========================================================================================================

    // Extract path info either from the 'real' URL path, or from an request parameter
    private static String extractPathInfo(String pPathInfo, ProcessingParameters pProcessingParams) {
        String pathInfo = pPathInfo;

        // If no pathinfo is given directly, we look for a query parameter named 'p'.
        // This variant is helpful, if there are problems with the server mangling
        // up the pathinfo (e.g. for security concerns, often '/','\',';' and other are not
        // allowed in encoded form within the pathinfo)
        if (pProcessingParams != null && (pPathInfo == null || pPathInfo.length() == 0 || pathInfo.matches("^/+$"))) {
            pathInfo = pProcessingParams.getPathInfo();
        }
        return normalizePathInfo(pathInfo);
    }



    // Return always a non-null string and strip of leading slash
    private static String normalizePathInfo(String pPathInfo) {
        if (pPathInfo != null && pPathInfo.length() > 0) {
            return pPathInfo.startsWith("/") ? pPathInfo.substring(1) : pPathInfo;
        } else {
            return "";
        }
    }

    // ==================================================================================
    // Dedicated creator for the various operations. They are installed as static processors.

    // Get the request creator for a specific type
    private static RequestCreator getCreator(RequestType pType) {
        RequestCreator creator = CREATOR_MAP.get(pType);
        if (creator == null) {
            throw new UnsupportedOperationException("Type " + pType + " is not supported (yet)");
        }
        return creator;
    }

    private static final Map<RequestType,RequestCreator> CREATOR_MAP;

    static {
        CREATOR_MAP = new HashMap<RequestType, RequestCreator>();
        CREATOR_MAP.put(RequestType.READ, JmxReadRequest.newCreator());
        CREATOR_MAP.put(RequestType.WRITE, JmxWriteRequest.newCreator());
        CREATOR_MAP.put(RequestType.EXEC, JmxExecRequest.newCreator());
        CREATOR_MAP.put(RequestType.LIST, JmxListRequest.newCreator());
        CREATOR_MAP.put(RequestType.VERSION, JmxVersionRequest.newCreator());
        CREATOR_MAP.put(RequestType.SEARCH, JmxSearchRequest.newCreator());
    }

}
