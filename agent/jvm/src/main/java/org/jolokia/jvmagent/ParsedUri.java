package org.jolokia.jvmagent;

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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.*;

/**
 * Enhanced URI class in order to provide means to get to the query string
 * and the request parameters
 *
 * @author roland
 * @since Mar 21, 2010
 */
public class ParsedUri {

    // Map of parameters as parsed from an URL
    private Map<String, String[]> parameters;

    // Pathinfo contained in the URL
    private String pathInfo;

    // Enclosed URI
    private URI uri;

    /**
     * Constructor
     *
     * @param pUri URI to parse
     * @param pContext an optional context which is tripped from the path itself
     */
    public ParsedUri(URI pUri,String ... pContext) {
        uri = pUri;
        pathInfo = pUri.getPath();

        if (pContext != null && pContext.length > 0 &&
                pathInfo.startsWith(pContext[0])) {
            pathInfo = pathInfo.substring(pContext[0].length());
        }

        while (pathInfo.startsWith("/") && pathInfo.length() > 1) {
            pathInfo = pathInfo.substring(1);
        }

        if (pUri.getQuery() != null) {
            parameters = parseQuery(pUri.getQuery());
        } else {
            parameters = new HashMap<String, String[]>();
        }
    }

    /**
     * Return the pathinfo fo this query object
     *
     * @return path info
     */
    public String getPathInfo() {
        return pathInfo;
    }

    /**
     * Get the complete URI which was parsed
     *
     * @return original URI
     */
    public URI getUri() {
        return uri;
    }

    /**
     * Get a single parameter of the parsed URI
     *
     * @param name parameter name
     * @return the value of the parameter or <code>null</code>
     *         if no such parameter is stored
     */
    public String getParameter(String name) {
        String[] values = parameters.get(name);
        if (values == null) {
            return null;
        }

        if (values.length == 0) {
            return "";
        }
        return values[0];
    }

    /**
     * Get the map with parsed parameters as key-value pairs, where the value is multi valued
     * (array of value)
     *
     * @return the parameter map
     */
    public Map<String, String[]> getParameterMap() {
        return parameters;
    }

    // parse the query
    private Map<String, String[]> parseQuery(String qs) {
        Map<String, String[]> ret = new TreeMap<String, String[]>();

        try {
            String pairs[] = qs.split("&");
            for (String pair : pairs) {
                String name;
                String value;
                int pos = pair.indexOf('=');
                // for "name=", the value is "", for "name" alone, the value is null
                if (pos == -1) {
                    name = pair;
                    value = null;
                } else {
                    name = URLDecoder.decode(pair.substring(0, pos), "UTF-8");
                    value = URLDecoder.decode(pair.substring(pos + 1, pair.length()), "UTF-8");
                }
                String[] values = ret.get(name);
                if (values == null) {
                    values = new String[]{value};
                    ret.put(name, values);
                } else {
                    // That's not a very cheap algorithm to create new arrays on the fly,
                    // but it is expected that there will be only a handful of array parameters
                    // in an URL anyway. So, let us be dirty here ...
                    String[] newValues = new String[values.length + 1];
                    System.arraycopy(values,0,newValues,0,values.length);
                    newValues[values.length] = value;
                    ret.put(name, newValues);
                }
            }
            return ret;
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Cannot decode to UTF-8. Should not happen, though.",e);
        }
    }

    @Override
    /** {@inheritDoc} */
    public String toString() {
        return uri.toString();
    }
}
