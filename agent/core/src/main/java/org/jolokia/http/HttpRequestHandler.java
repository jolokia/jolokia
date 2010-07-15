package org.jolokia.http;

import org.jolokia.*;
import org.jolokia.backend.BackendManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.management.*;
import java.io.*;
import java.util.List;
import java.util.Map;

/*
 * jmx4perl - WAR Agent for exporting JMX via JSON
 *
 * Copyright (C) 2009 Roland Huß, roland@cpan.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * A commercial license is available as well. Please contact roland@cpan.org for
 * further details.
 */

/**
 * Request handler with no dependency on the servlet API so that it can be used in
 * several different environments (like for the Sun JDK 6 {@link com.sun.net.httpserver.HttpServer}.
 *
 * @author roland
 * @since Mar 3, 2010
 */
public class HttpRequestHandler {

    // handler for contacting the MBean server(s)
    private BackendManager backendManager;

    // Logging abstraction
    private LogHandler logHandler;

    /**
     * Request handler for parsing HTTP request and dispatching to the appropriate
     * request handler (with help of the backend manager)
     *
     * @param pBackendManager backend manager to user
     * @param pLogHandler log handler to where to put out logging
     */
    public HttpRequestHandler(BackendManager pBackendManager, LogHandler pLogHandler) {
        backendManager = pBackendManager;
        logHandler = pLogHandler;
    }


    /**
     * Handle the input stream as given by a POST request
     *
     * @param pInputStream inputstream of the post request
     * @param pEncoding optional encoding for the stream. If null, the default encoding is used
     * @return the JSON object containing the json results for one or more {@link JmxRequest} contained
     *         within the answer.
     *
     * @throws MalformedObjectNameException if one or more request contain an invalid MBean name
     * @throws IOException if reading from the input stream fails
     */
    public JSONAware handleRequestInputStream(InputStream pInputStream, String pEncoding)
            throws MalformedObjectNameException, IOException {
        JSONAware jsonRequest = extractJsonRequest(pInputStream,pEncoding);
        if (jsonRequest instanceof List) {
            List<JmxRequest> jmxRequests = JmxRequestFactory.createRequestsFromJson((List) jsonRequest);

            JSONArray responseList = new JSONArray();
            for (JmxRequest jmxReq : jmxRequests) {
                boolean debug = backendManager.isDebug() && !"debugInfo".equals(jmxReq.getOperation());
                if (debug) {
                    logHandler.debug("Request: " + jmxReq.toString());
                }
                // Call handler and retrieve return value
                JSONObject resp = executeRequest(jmxReq);
                responseList.add(resp);
            }
            return responseList;
        } else if (jsonRequest instanceof Map) {
            JmxRequest jmxReq = JmxRequestFactory.createSingleRequestFromJson((Map<String, ?>) jsonRequest);
            return executeRequest(jmxReq);
        } else {
            throw new IllegalArgumentException("Invalid JSON Request " + jsonRequest.toJSONString());
        }
    }

    private JSONAware extractJsonRequest(InputStream pInputStream, String pEncoding) throws IOException {
        InputStreamReader reader = null;
        try {
            reader =
                    pEncoding != null ?
                            new InputStreamReader(pInputStream, pEncoding) :
                            new InputStreamReader(pInputStream);
            JSONParser parser = new JSONParser();
            return (JSONAware) parser.parse(reader);
        } catch (ParseException exp) {
            throw new IllegalArgumentException("Invalid JSON request " + reader,exp);
        }
    }

    /**
     * Execute a single {@link org.jolokia.JmxRequest}. If a checked  exception occurs,
     * this gets translated into the appropriate JSON object which will get returned.
     *
     * @param pJmxReq the request to execute
     * @return the JSON representation of the answer.
     */
    public JSONObject executeRequest(JmxRequest pJmxReq) {
        // Call handler and retrieve return value
        try {
            return backendManager.handleRequest(pJmxReq);
        } catch (ReflectionException e) {
            return getErrorJSON(404,e);
        } catch (InstanceNotFoundException e) {
            return getErrorJSON(404,e);
        } catch (MBeanException e) {
            return getErrorJSON(500,e);
        } catch (AttributeNotFoundException e) {
            return getErrorJSON(404,e);
        } catch (UnsupportedOperationException e) {
            return getErrorJSON(500,e);
        } catch (IOException e) {
            return getErrorJSON(500,e);
        }
    }

    /**
     * Utilit method for handling single runtime exceptions and errors.
     *
     * @param exp exception to handle
     * @return its JSON representation
     */
    public JSONObject handleThrowable(Throwable pThrowable) {
        JSONObject json;
        Throwable exp = pThrowable;
        if (exp instanceof RuntimeMBeanException) {
            // Unwrap
            exp = exp.getCause();
        }
        if (exp instanceof IllegalArgumentException) {
            json = getErrorJSON(400,exp);
        } else if (exp instanceof IllegalStateException) {
            json = getErrorJSON(500,exp);
        } else if (exp instanceof SecurityException) {
            // Wipe out stacktrace
            json = getErrorJSON(403,new Exception(exp.getMessage()));
        } else {
            json = getErrorJSON(500,exp);
        }
        return json;
    }


    /**
     * Get the JSON representation for a an exception
     *
     * @param pErrorCode the HTTP error code to return
     * @param pExp the exception or error occured
     * @return the json representation
     */
    public JSONObject getErrorJSON(int pErrorCode, Throwable pExp) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("status",pErrorCode);
        jsonObject.put("error",pExp.toString());
        StringWriter writer = new StringWriter();
        pExp.printStackTrace(new PrintWriter(writer));
        jsonObject.put("stacktrace",writer.toString());
        if (backendManager.isDebug()) {
            backendManager.error("Error " + pErrorCode,pExp);
        }
        return jsonObject;
    }


    /**
     * Check whether the given host and/or address is allowed to access this agent.
     *
     * @param pHost host to check
     * @param pAddress address to check
     */
    public void checkClientIPAccess(String pHost, String pAddress) {
        if (!backendManager.isRemoteAccessAllowed(pHost,pAddress)) {
            throw new SecurityException("No access from client " + pAddress + " allowed");
        }
    }

    /**
     * Extract the the result code for a JSON answer. If multiple responses are contained,
     * the result code is the highest code found within the list of responses
     *
     * @param pJson response object
     * @return the result code
     */
    public int extractResultCode(JSONAware pJson) {
        if (pJson instanceof List) {
            int maxCode = 0;
            for (JSONAware j : (List<JSONAware>) pJson) {
                int code = extractStatus(j);
                if (code > maxCode) {
                    maxCode = code;
                }
            }
            return maxCode;
        } else {
            return extractStatus(pJson);
        }
    }

    // Extract status from a json object
    private int extractStatus(JSONAware pJson) {
        if (pJson instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) pJson;
            if (!jsonObject.containsKey("status")) {
                throw new IllegalStateException("No status given in response " + pJson);
            }
            return (Integer) jsonObject.get("status");
        } else {
            throw new IllegalStateException("Internal: Not a JSONObject but a " + pJson.getClass() + " " + pJson);
        }
    }
}
