package org.jolokia.http;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.*;
import java.net.SocketException;
import java.util.*;

import javax.management.JMException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jolokia.backend.TestDetector;
import org.jolokia.config.ConfigKey;
import org.jolokia.discovery.JolokiaDiscovery;
import org.jolokia.restrictor.*;
import org.jolokia.test.util.HttpTestUtil;
import org.jolokia.util.LogHandler;
import org.jolokia.util.NetworkUtil;
import org.jolokia.util.QuietLogHandler;
import org.json.simple.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 30.08.11
 */
public class AgentServletTest {

    private ServletContext context;
    private ServletConfig config;

    private HttpServletRequest request;
    private HttpServletResponse response;

    private AgentServlet servlet;

    @Test
    public void simpleInit() throws ServletException {
        servlet = new AgentServlet();
        initConfigMocks(null, null,"No access restrictor found", null);
        replay(config, context);

        servlet.init(config);
        servlet.destroy();
    }

    @Test
    public void initWithAcessRestriction() throws ServletException {
        servlet = new AgentServlet();
        initConfigMocks(new String[]{ConfigKey.POLICY_LOCATION.getKeyValue(), "classpath:/access-sample1.xml"},
                        null,
                        "Using access restrictor.*access-sample1.xml", null);
        replay(config, context);

        servlet.init(config);
        servlet.destroy();
    }

    @Test
    public void initWithInvalidPolicyFile() throws ServletException {
        servlet = new AgentServlet();
        initConfigMocks(new String[]{ConfigKey.POLICY_LOCATION.getKeyValue(), "file:///blablub.xml"},
                        null,
                        "Error.*blablub.xml.*Denying", FileNotFoundException.class);
        replay(config, context);

        servlet.init(config);
        servlet.destroy();
    }

    @Test
    public void configWithOverWrite() throws ServletException {
        servlet = new AgentServlet();
        request = createMock(HttpServletRequest.class);
        response = createMock(HttpServletResponse.class);
        initConfigMocks(new String[] {ConfigKey.AGENT_CONTEXT.getKeyValue(),"/jmx4perl",ConfigKey.MAX_DEPTH.getKeyValue(),"10"},
                        new String[] {ConfigKey.AGENT_CONTEXT.getKeyValue(),"/j0l0k14",ConfigKey.MAX_OBJECTS.getKeyValue(),"20",
                                      ConfigKey.CALLBACK.getKeyValue(),"callback is a request option, must be empty here"},
                        null,null);
        replay(config, context,request,response);

        servlet.init(config);
        servlet.destroy();

        org.jolokia.config.Configuration cfg = servlet.initConfig(config);
        assertEquals(cfg.get(ConfigKey.AGENT_CONTEXT), "/j0l0k14");
        assertEquals(cfg.get(ConfigKey.MAX_DEPTH), "10");
        assertEquals(cfg.get(ConfigKey.MAX_OBJECTS), "20");
        assertNull(cfg.get(ConfigKey.CALLBACK));
        assertNull(cfg.get(ConfigKey.DETECTOR_OPTIONS));

    }

    @Test
    public void initWithCustomAccessRestrictor() throws ServletException {
        prepareStandardInitialisation();
        servlet.destroy();
    }

    @Test
    public void initWithCustomLogHandler() throws Exception {
        servlet = new AgentServlet();
        config = createMock(ServletConfig.class);
        context = createMock(ServletContext.class);

        HttpTestUtil.prepareServletConfigMock(config, new String[]{ConfigKey.LOGHANDLER_CLASS.getKeyValue(), CustomLogHandler.class.getName()});
        HttpTestUtil.prepareServletContextMock(context);

        expect(config.getServletContext()).andStubReturn(context);
        expect(config.getServletName()).andStubReturn("jolokia");
        replay(config, context);

        servlet.init(config);
        servlet.destroy();

        assertTrue(CustomLogHandler.infoCount > 0);
    }

    @Test
    public void initWithAgentDiscoveryAndGivenUrl() throws ServletException, IOException, InterruptedException {
        checkMulticastAvailable();
        String url = "http://localhost:8080/jolokia";
        prepareStandardInitialisation(ConfigKey.DISCOVERY_AGENT_URL.getKeyValue(), url);
        String multicastGroup = ConfigKey.MULTICAST_GROUP.getDefaultValue();
        int multicastPort = Integer.valueOf(ConfigKey.MULTICAST_PORT.getDefaultValue());
        // Wait listening thread to warm up
        Thread.sleep(1000);
        try {
            JolokiaDiscovery discovery = new JolokiaDiscovery("test", new QuietLogHandler());
            List<JSONObject> in = discovery.lookupAgentsWithTimeoutAndMulticastAddress(500, multicastGroup, multicastPort);
            for (JSONObject json : in) {
                if (json.get("url") != null && json.get("url").equals(url)) {
                    return;
                }
            }
            fail("No agent found");
        } finally {
            servlet.destroy();
        }
    }

    @Test
    public void initWithAgentDiscoveryAndUrlLookup() throws ServletException, IOException {
        checkMulticastAvailable();
        prepareStandardInitialisation(ConfigKey.DISCOVERY_ENABLED.getKeyValue(), "true");
        try {
            JolokiaDiscovery discovery = new JolokiaDiscovery("test", new QuietLogHandler());
            List<JSONObject> in = discovery.lookupAgents();
            assertTrue(in.size() > 0);
            // At least one doesnt have an URL (remove this part if a way could be found for getting
            // to the URL
            for (JSONObject json : in) {
                if (json.get("url") == null) {
                    return;
                }
            }
            fail("Every message has an URL");
        } finally {
            servlet.destroy();
        }
    }

    private void checkMulticastAvailable() throws SocketException {
        if (!NetworkUtil.isMulticastSupported()) {
            throw new SkipException("No multicast interface found, skipping test ");
        }
    }

    @Test
    public void initWithAgentDiscoveryAndUrlCreationAfterGet() throws ServletException, IOException {
        checkMulticastAvailable();
        prepareStandardInitialisation(ConfigKey.DISCOVERY_ENABLED.getKeyValue(), "true");
        try {
            String url = "http://10.9.11.1:9876/jolokia";
            ByteArrayOutputStream sw = initRequestResponseMocks(
                    getDiscoveryRequestSetup(url),
                    getStandardResponseSetup());
            replay(request, response);

            servlet.doGet(request, response);

            assertTrue(sw.toString().contains("used"));

            JolokiaDiscovery discovery = new JolokiaDiscovery("test",new QuietLogHandler());
            List<JSONObject> in = discovery.lookupAgents();
            assertTrue(in.size() > 0);
            for (JSONObject json : in) {
                if (json.get("url") != null && json.get("url").equals(url)) {
                    assertTrue((Boolean) json.get("secured"));
                    return;
                }
            }
            fail("Failed, because no message had an URL");
        } finally {
            servlet.destroy();
        }
    }


    public static class CustomLogHandler implements LogHandler {

        private static int infoCount = 0;

        public CustomLogHandler() {
            infoCount = 0;
        }

        public void debug(String message) {
        }

        public void info(String message) {
            infoCount++;
        }

        public void error(String message, Throwable t) {
        }
    }

    @Test
    public void simpleGet() throws ServletException, IOException {
        prepareStandardInitialisation();

        ByteArrayOutputStream sw = initRequestResponseMocks();
        expect(request.getPathInfo()).andReturn(HttpTestUtil.HEAP_MEMORY_GET_REQUEST);
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn("text/plain");
        expect(request.getAttribute("subject")).andReturn(null);
        replay(request, response);

        servlet.doGet(request, response);

        assertTrue(sw.toString().contains("used"));
        servlet.destroy();
    }

    @Test
    public void simpleGetWithWrongMimeType() throws ServletException, IOException {
        checkMimeTypes("text/html", "text/plain");
    }

    @Test
    public void simpleGetWithTextPlainMimeType() throws ServletException, IOException {
        checkMimeTypes("text/plain", "text/plain");
    }

    @Test
    public void simpleGetWithApplicationJsonMimeType() throws ServletException, IOException {
        checkMimeTypes("application/json", "application/json");
    }

    private void checkMimeTypes(String given, final String expected) throws ServletException, IOException {
        prepareStandardInitialisation();

        initRequestResponseMocks(
            getStandardRequestSetup(),
            new Runnable() {
                public void run() {
                    response.setCharacterEncoding("utf-8");
                    // The default content type
                    response.setContentType(expected);
                    response.setStatus(200);
                }
            });
        expect(request.getPathInfo()).andReturn(HttpTestUtil.HEAP_MEMORY_GET_REQUEST);
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn(given);
        replay(request, response);

        servlet.doGet(request, response);

        verifyMocks();
        servlet.destroy();
    }

    @Test
    public void simpleGetWithNoReverseDnsLookupFalse() throws ServletException, IOException {
        checkNoReverseDns(false,"127.0.0.1");
    }

    @Test
    public void simpleGetWithNoReverseDnsLookupTrue() throws ServletException, IOException {
        checkNoReverseDns(true,"localhost","127.0.0.1");
    }

    private void checkNoReverseDns(boolean enabled, String ... expectedHosts) throws ServletException, IOException {
        prepareStandardInitialisation(
                (Restrictor) null,
                ConfigKey.RESTRICTOR_CLASS.getKeyValue(),NoDnsLookupRestrictorChecker.class.getName(),
                ConfigKey.ALLOW_DNS_REVERSE_LOOKUP.getKeyValue(),Boolean.toString(enabled));
        NoDnsLookupRestrictorChecker.expectedHosts = expectedHosts;
        ByteArrayOutputStream sw = initRequestResponseMocks();
        expect(request.getPathInfo()).andReturn(HttpTestUtil.HEAP_MEMORY_GET_REQUEST);
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn("text/plain");
        expect(request.getAttribute("subject")).andReturn(null);
        replay(request, response);

        servlet.doGet(request, response);

        assertFalse(sw.toString().contains("error"));
        servlet.destroy();
    }


    // Check whether restrictor is called with the proper args
    public static class NoDnsLookupRestrictorChecker extends AbstractConstantRestrictor {

        static String[] expectedHosts;

        public NoDnsLookupRestrictorChecker() {
            super(true);
        }

        @Override
        public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
            if (expectedHosts.length != pHostOrAddress.length) {
                return false;
            }
            for (int i = 0; i < expectedHosts.length; i++) {
                if (!expectedHosts[i].equals(pHostOrAddress[i])) {
                    return false;
                }
            }
            return true;
        }


    }

    @Test
    public void simpleGetWithUnsupportedGetParameterMapCall() throws ServletException, IOException {
        prepareStandardInitialisation();
        ByteArrayOutputStream sw = initRequestResponseMocks(
                new Runnable() {
                    public void run() {
                        expect(request.getHeader("Origin")).andStubReturn(null);
                        expect(request.getHeader("Referer")).andStubReturn(null);
                        expect(request.getRemoteHost()).andReturn("localhost");
                        expect(request.getRemoteAddr()).andReturn("127.0.0.1");
                        expect(request.getRequestURI()).andReturn("/jolokia/");
                        setupAgentDetailsInitExpectations();
                        expect(request.getPathInfo()).andReturn(HttpTestUtil.HEAP_MEMORY_GET_REQUEST);
                        expect(request.getParameterMap()).andThrow(new UnsupportedOperationException(""));
                        expect(request.getAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE)).andReturn(null);
                        Vector params = new Vector();
                        params.add("debug");
                        expect(request.getParameterNames()).andReturn(params.elements());
                        expect(request.getParameterValues("debug")).andReturn(new String[] {"false"});
                        expect(request.getAttribute("subject")).andReturn(null);
                        expect(request.getParameter(ConfigKey.STREAMING.getKeyValue())).andReturn(null);

                    }
                },
                getStandardResponseSetup());
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn(null);
        replay(request,response);

        servlet.doGet(request,response);
        servlet.destroy();
    }


    @Test
    public void simplePost() throws ServletException, IOException {
        prepareStandardInitialisation();

        ByteArrayOutputStream responseWriter = initRequestResponseMocks();
        expect(request.getCharacterEncoding()).andReturn("utf-8");
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn("text/plain");
        expect(request.getAttribute("subject")).andReturn(null);

        preparePostRequest(HttpTestUtil.HEAP_MEMORY_POST_REQUEST);

        replay(request, response);

        servlet.doPost(request, response);

        assertTrue(responseWriter.toString().contains("used"));
        servlet.destroy();
    }

    @Test
    public void unknownMethodWhenSettingContentType() throws ServletException, IOException {
        prepareStandardInitialisation();

        ByteArrayOutputStream sw = initRequestResponseMocks(
                getStandardRequestSetup(),
                new Runnable() {
                    public void run() {
                        response.setCharacterEncoding("utf-8");
                        expectLastCall().andThrow(new NoSuchMethodError());
                        response.setContentType("text/plain; charset=utf-8");
                        response.setStatus(200);
                    }
                });
        expect(request.getPathInfo()).andReturn(HttpTestUtil.HEAP_MEMORY_GET_REQUEST);
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn(null);
        expect(request.getAttribute("subject")).andReturn(null);

        replay(request, response);

        servlet.doGet(request, response);

        assertTrue(sw.toString().contains("used"));
        servlet.destroy();
    }

    @Test
    public void corsPreflightCheck() throws ServletException, IOException {
        checkCorsOriginPreflight("http://bla.com", "http://bla.com");
    }

    @Test
    public void corsPreflightCheckWithNullOrigin() throws ServletException, IOException {
        checkCorsOriginPreflight("null", "*");
    }

    private void checkCorsOriginPreflight(String in, String out) throws ServletException, IOException {
        prepareStandardInitialisation();
        request = createMock(HttpServletRequest.class);
        response = createMock(HttpServletResponse.class);

        expect(request.getHeader("Origin")).andReturn(in);
        expect(request.getHeader("Access-Control-Request-Headers")).andReturn(null);

        response.setHeader(eq("Access-Control-Max-Age"), (String) anyObject());
        response.setHeader("Access-Control-Allow-Origin", out);
        response.setHeader("Access-Control-Allow-Credentials", "true");

        replay(request, response);

        servlet.doOptions(request, response);
        servlet.destroy();
    }


    @Test
    public void corsHeaderGetCheck() throws ServletException, IOException {
        checkCorsGetOrigin("http://bla.com","http://bla.com");
    }

    @Test
    public void corsHeaderGetCheckWithNullOrigin() throws ServletException, IOException {
        checkCorsGetOrigin("null","*");
    }

    private void checkCorsGetOrigin(final String in, final String out) throws ServletException, IOException {
        prepareStandardInitialisation();

        ByteArrayOutputStream sw = initRequestResponseMocks(
                new Runnable() {
                    public void run() {
                        expect(request.getParameter(ConfigKey.STREAMING.getKeyValue())).andReturn(null);
                        expect(request.getHeader("Origin")).andStubReturn(in);
                        expect(request.getRemoteHost()).andReturn("localhost");
                        expect(request.getRemoteAddr()).andReturn("127.0.0.1");
                        expect(request.getRequestURI()).andReturn("/jolokia/").times(2);
                        expect(request.getRequestURL()).andReturn(new StringBuffer("http://localhost/jolokia"));
                        expect(request.getContextPath()).andReturn("/jolokia");
                        expect(request.getAuthType()).andReturn(null);
                        expect(request.getParameterMap()).andReturn(null);
                        expect(request.getAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE)).andReturn(null);
                        expect(request.getAttribute("subject")).andReturn(null);
                    }
                },
                new Runnable() {
                    public void run() {
                        response.setHeader("Access-Control-Allow-Origin", out);
                        response.setHeader("Access-Control-Allow-Credentials","true");
                        response.setCharacterEncoding("utf-8");
                        response.setContentType("text/plain");
                        response.setStatus(200);
                    }
                }

        );
        expect(request.getPathInfo()).andReturn(HttpTestUtil.HEAP_MEMORY_GET_REQUEST);
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn("text/plain");
        replay(request, response);

        servlet.doGet(request, response);

        servlet.destroy();
    }

    private void setNoCacheHeaders(HttpServletResponse pResp) {
        pResp.setHeader("Cache-Control", "no-cache");
        pResp.setHeader("Pragma","no-cache");
        pResp.setDateHeader(eq("Date"),anyLong());
        pResp.setDateHeader(eq("Expires"),anyLong());
    }

    @Test
    public void withCallback() throws IOException, ServletException {
        prepareStandardInitialisation();

        ByteArrayOutputStream sw = initRequestResponseMocks(
                "myCallback",
                getStandardRequestSetup(),
                new Runnable() {
                    public void run() {
                        response.setCharacterEncoding("utf-8");
                        response.setContentType("text/javascript");
                        response.setStatus(200);
                    }
                });
        expect(request.getPathInfo()).andReturn(HttpTestUtil.HEAP_MEMORY_GET_REQUEST);
        expect(request.getAttribute("subject")).andReturn(null);
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn(null);

        replay(request, response);

        servlet.doGet(request, response);

        assertTrue(sw.toString().matches("^myCallback\\(.*\\);$"));
        servlet.destroy();
    }

    @Test
    public void withInvalidCallback() throws IOException, ServletException {
        servlet = new AgentServlet(new AllowAllRestrictor());
        initConfigMocks(null, null,"Error 400", IllegalArgumentException.class);
        replay(config, context);
        servlet.init(config);
        ByteArrayOutputStream sw = initRequestResponseMocks(
            "doSomethingEvil(); myCallback",
            getStandardRequestSetup(),
            getStandardResponseSetup());
        expect(request.getPathInfo()).andReturn(HttpTestUtil.HEAP_MEMORY_GET_REQUEST);
        expect(request.getAttribute("subject")).andReturn(null);
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn(null);

        replay(request, response);

        servlet.doGet(request, response);
        String resp = sw.toString();
        assertTrue(resp.contains("error_type"));
        assertTrue(resp.contains("IllegalArgumentException"));
        assertTrue(resp.matches(".*status.*400.*"));
        servlet.destroy();
    }

    @Test
    public void withException() throws ServletException, IOException {
        servlet = new AgentServlet(new AllowAllRestrictor());
        initConfigMocks(null, null,"Error 500", IllegalStateException.class);
        replay(config, context);
        servlet.init(config);
        ByteArrayOutputStream sw = initRequestResponseMocks(
                new Runnable() {
                    public void run() {
                        expect(request.getHeader("Origin")).andReturn(null);
                        expect(request.getRemoteHost()).andThrow(new IllegalStateException());
                    }
                },
                getStandardResponseSetup());
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn("text/plain");
        expect(request.getParameter(ConfigKey.STREAMING.getKeyValue())).andReturn(null);

        replay(request, response);

        servlet.doGet(request, response);
        String resp = sw.toString();
        assertTrue(resp.contains("error_type"));
        assertTrue(resp.contains("IllegalStateException"));
        assertTrue(resp.matches(".*status.*500.*"));
        servlet.destroy();
        verify(config, context, request, response);
    }


    @Test
    public void debug() throws IOException, ServletException {
        servlet = new AgentServlet();
        initConfigMocks(new String[]{ConfigKey.DEBUG.getKeyValue(), "true"},null,"No access restrictor found",null);
        context.log(find("URI:"));
        context.log(find("Path-Info:"));
        context.log(find("Request:"));
        context.log(find("time:"));
        context.log(find("Response:"));
        context.log(find("TestDetector"),isA(RuntimeException.class));
        expectLastCall().asStub();
        replay(config, context);

        servlet.init(config);

        ByteArrayOutputStream sw = initRequestResponseMocks();
        expect(request.getPathInfo()).andReturn(HttpTestUtil.HEAP_MEMORY_GET_REQUEST);
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn(null);
        expect(request.getAttribute("subject")).andReturn(null);
        replay(request, response);

        servlet.doGet(request, response);

        assertTrue(sw.toString().contains("used"));
        servlet.destroy();
    }


    @BeforeMethod
    void resetTestDetector() {
        TestDetector.reset();
    }

    //@AfterMethod
    public void verifyMocks() {
        verify(config, context, request, response);
    }
    // ============================================================================================

    private void initConfigMocks(String[] pInitParams, String[] pContextParams,String pLogRegexp, Class<? extends Exception> pExceptionClass) {
        config = createMock(ServletConfig.class);
        context = createMock(ServletContext.class);


        String[] params = pInitParams != null ? Arrays.copyOf(pInitParams,pInitParams.length + 2) : new String[2];
        params[params.length - 2] = ConfigKey.DEBUG.getKeyValue();
        params[params.length - 1] = "true";
        HttpTestUtil.prepareServletConfigMock(config,params);
        HttpTestUtil.prepareServletContextMock(context, pContextParams);


        expect(config.getServletContext()).andStubReturn(context);
        expect(config.getServletName()).andStubReturn("jolokia");
        if (pExceptionClass != null) {
            context.log(find(pLogRegexp),isA(pExceptionClass));
        } else {
            if (pLogRegexp != null) {
                context.log(find(pLogRegexp));
            } else {
                context.log((String) anyObject());
            }
        }
        context.log((String) anyObject());
        expectLastCall().asStub();
        context.log(find("TestDetector"),isA(RuntimeException.class));
        context.log((String) anyObject(),isA(JMException.class));
        expectLastCall().anyTimes();
    }

    private ByteArrayOutputStream initRequestResponseMocks() throws IOException {
        return initRequestResponseMocks(
                getStandardRequestSetup(),
                getStandardResponseSetup());
    }

    private ByteArrayOutputStream initRequestResponseMocks(Runnable requestSetup,Runnable responseSetup) throws IOException {
        return initRequestResponseMocks(null,requestSetup,responseSetup);
    }

    private ByteArrayOutputStream initRequestResponseMocks(String callback,Runnable requestSetup,Runnable responseSetup) throws IOException {
        request = createMock(HttpServletRequest.class);
        response = createMock(HttpServletResponse.class);
        setNoCacheHeaders(response);

        expect(request.getParameter(ConfigKey.CALLBACK.getKeyValue())).andReturn(callback).anyTimes();
        requestSetup.run();
        responseSetup.run();

        class MyServletOutputStream extends ServletOutputStream {
            ByteArrayOutputStream baos;
            public void write(int b) throws IOException {
                baos.write(b);
            }

            public void setBaos(ByteArrayOutputStream baos){
                this.baos = baos;
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MyServletOutputStream sos = new MyServletOutputStream();
        sos.setBaos(baos);
        expect(response.getOutputStream()).andReturn(sos);

        return baos;
    }

    private void preparePostRequest(String pReq) throws IOException {
        ServletInputStream is = HttpTestUtil.createServletInputStream(pReq);
        expect(request.getInputStream()).andReturn(is);
    }

    private void prepareStandardInitialisation(Restrictor restrictor, String ... params) throws ServletException {
        servlet = new AgentServlet(restrictor);
        initConfigMocks(params.length > 0 ? params : null, null,"custom access", null);
        replay(config, context);
        servlet.init(config);
    }

    private void prepareStandardInitialisation(String ... params) throws ServletException {
        prepareStandardInitialisation(new AllowAllRestrictor(),params);
    }

    private Runnable getStandardResponseSetup() {
        return new Runnable() {
            public void run() {
                response.setCharacterEncoding("utf-8");
                // The default content type
                response.setContentType("text/plain");
                response.setStatus(200);
            }
        };
    }

    private Runnable getStandardRequestSetup() {
        return new Runnable() {
            public void run() {
                expect(request.getHeader("Origin")).andStubReturn(null);
                expect(request.getHeader("Referer")).andStubReturn(null);
                expect(request.getRemoteHost()).andStubReturn("localhost");
                expect(request.getRemoteAddr()).andStubReturn("127.0.0.1");
                expect(request.getRequestURI()).andReturn("/jolokia/");
                setupAgentDetailsInitExpectations();
                expect(request.getParameterMap()).andReturn(null);
                expect(request.getAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE)).andReturn(null);
                expect(request.getParameter(ConfigKey.STREAMING.getKeyValue())).andReturn(null);

            }
        };
    }

    private void setupAgentDetailsInitExpectations() {
        expect(request.getRequestURI()).andReturn("/jolokia/");
        expect(request.getRequestURL()).andReturn(new StringBuffer("http://localhost/jolokia"));
        expect(request.getContextPath()).andReturn("/jolokia/");
        expect(request.getAuthType()).andReturn(null);
    }

    private Runnable getDiscoveryRequestSetup(final String url) {
        return new Runnable() {
            public void run() {
                expect(request.getHeader("Origin")).andStubReturn(null);
                expect(request.getHeader("Referer")).andStubReturn(null);
                expect(request.getRemoteHost()).andReturn("localhost");
                expect(request.getRemoteAddr()).andReturn("127.0.0.1");
                expect(request.getRequestURI()).andReturn("/jolokia/");
                expect(request.getParameterMap()).andReturn(null);
                expect(request.getAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE)).andReturn(null);

                expect(request.getPathInfo()).andReturn(HttpTestUtil.HEAP_MEMORY_GET_REQUEST);
                expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn("text/plain").anyTimes();
                StringBuffer buf = new StringBuffer();
                buf.append(url).append(HttpTestUtil.HEAP_MEMORY_GET_REQUEST);
                expect(request.getRequestURL()).andReturn(buf);
                expect(request.getRequestURI()).andReturn("/jolokia" + HttpTestUtil.HEAP_MEMORY_GET_REQUEST);
                expect(request.getContextPath()).andReturn("/jolokia");
                expect(request.getAuthType()).andReturn("BASIC");
                expect(request.getAttribute("subject")).andReturn(null);
                expect(request.getParameter(ConfigKey.STREAMING.getKeyValue())).andReturn(null);

            }
        };
    }


}
