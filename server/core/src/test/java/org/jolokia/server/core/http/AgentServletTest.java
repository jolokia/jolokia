package org.jolokia.server.core.http;

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
import java.util.Arrays;
import java.util.Map;
import java.util.Vector;

import javax.management.JMException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.easymock.EasyMock;
import org.jolokia.server.core.Version;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.Configuration;
import org.jolokia.server.core.restrictor.AbstractConstantRestrictor;
import org.jolokia.server.core.restrictor.AllowAllRestrictor;
import org.jolokia.server.core.service.api.*;
import org.jolokia.server.core.util.HttpTestUtil;
import org.jolokia.json.JSONObject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.jolokia.test.util.ReflectionTestUtil.getField;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 30.08.11
 */
public class AgentServletTest {

    private static final boolean DEBUG = false;
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

        Configuration cfg = servlet.createWebConfig();
        assertEquals(cfg.getConfig(ConfigKey.AGENT_CONTEXT), "/j0l0k14");
        assertEquals(cfg.getConfig(ConfigKey.MAX_DEPTH), "10");
        assertEquals(cfg.getConfig(ConfigKey.MAX_OBJECTS), "20");
        assertNull(cfg.getConfig(ConfigKey.CALLBACK));
        assertNull(cfg.getConfig(ConfigKey.DETECTOR_OPTIONS));

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

        HttpTestUtil.prepareServletConfigMock(config, ConfigKey.LOGHANDLER_CLASS.getKeyValue(), CustomLogHandler.class.getName());
        HttpTestUtil.prepareServletContextMock(context);

        expect(config.getServletContext()).andStubReturn(context);
        expect(config.getServletName()).andStubReturn("jolokia");
        replay(config, context);

        servlet.init(config);
        servlet.destroy();

        assertTrue(CustomLogHandler.infoCount > 0);
    }

    @Test
    public void initWithAgentDiscoveryAndGivenUrlAsSysProp() throws ServletException, NoSuchFieldException, IllegalAccessException {
        String url = "http://localhost:8080/jolokia";
        System.setProperty(ConfigKey.DISCOVERY_AGENT_URL.asSystemProperty(),url);
        System.setProperty(ConfigKey.DISCOVERY_ENABLED.asSystemProperty(),"true");
        try {
            prepareStandardInitialisation();
            JolokiaContext ctx = (JolokiaContext) getField(servlet, "jolokiaContext");
            assertEquals(ctx.getConfig(ConfigKey.DISCOVERY_AGENT_URL),url);
            JSONObject json = ctx.getAgentDetails().toJSONObject();
            assertEquals(json.get("url"),url);
            assertTrue(Boolean.parseBoolean(ctx.getConfig(ConfigKey.DISCOVERY_ENABLED)));
        } finally {
            System.clearProperty(ConfigKey.DISCOVERY_AGENT_URL.asSystemProperty());
            System.clearProperty(ConfigKey.DISCOVERY_ENABLED.asSystemProperty());
        }
    }

    @Test(enabled = false)
    public void initWithAgentDiscoveryAndUrlAsSysEnv() throws ServletException, NoSuchFieldException, IllegalAccessException {
        prepareStandardInitialisation();
        String url = "http://localhost:8080/jolokia";
        try {
            System.getenv().put(ConfigKey.DISCOVERY_AGENT_URL.asEnvVariable(),url);
            System.getenv().put(ConfigKey.DISCOVERY_ENABLED.asEnvVariable(),"true");
            prepareStandardInitialisation();
            JolokiaContext ctx = (JolokiaContext) getField(servlet, "jolokiaContext");
            assertEquals(ctx.getConfig(ConfigKey.DISCOVERY_AGENT_URL),url);
            JSONObject json = ctx.getAgentDetails().toJSONObject();
            assertEquals(json.get("url"),url);
            assertTrue(Boolean.parseBoolean(ctx.getConfig(ConfigKey.DISCOVERY_ENABLED)));
        } finally {
            System.getenv().put(ConfigKey.DISCOVERY_AGENT_URL.asEnvVariable(), null);
            System.getenv().put(ConfigKey.DISCOVERY_ENABLED.asEnvVariable(), null);
        }
    }

    @Test
    public void initWithAgentDiscoveryAndUrlCreationAfterGet() throws ServletException, IOException, NoSuchFieldException, IllegalAccessException {
        prepareStandardInitialisation(ConfigKey.DISCOVERY_ENABLED.getKeyValue(), "true");
        try {
            String url = "http://10.9.11.1:9876/jolokia";
            ByteArrayOutputStream sw = initRequestResponseMocks(
                    getDiscoveryRequestSetup(url),
                    getTextPlainResponseSetup());
            replay(request, response);

            servlet.doGet(request, response);

            assertTrue(sw.toString().contains("version"));

            JolokiaContext ctx = (JolokiaContext) getField(servlet, "jolokiaContext");
            JSONObject json = ctx.getAgentDetails().toJSONObject();
            assertEquals(json.get("url"),url);
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

        public boolean isDebug() {
            return false;
        }
    }

    @Test
    public void simpleGet() throws ServletException, IOException {
        prepareStandardInitialisation();

        ByteArrayOutputStream sw = initRequestResponseMocks();
        expect(request.getPathInfo()).andReturn(HttpTestUtil.VERSION_GET_REQUEST);
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn("text/plain");
        expect(request.getAttribute("subject")).andReturn(null);
        replay(request, response);

        servlet.doGet(request, response);

        assertTrue(sw.toString().contains(Version.getAgentVersion()));
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
            () -> {
                response.setCharacterEncoding("utf-8");
                // The default content type
                response.setContentType(expected);
                response.setStatus(200);
            });
        expect(request.getPathInfo()).andReturn(HttpTestUtil.VERSION_GET_REQUEST);
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
        expect(request.getPathInfo()).andReturn(HttpTestUtil.VERSION_GET_REQUEST);
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
                () -> {
                    expect(request.getHeader("Origin")).andStubReturn(null);
                    expect(request.getHeader("Referer")).andStubReturn(null);
                    expect(request.getRemoteHost()).andReturn("localhost");
                    expect(request.getRemoteAddr()).andReturn("127.0.0.1");
                    expect(request.getRequestURI()).andReturn("/jolokia/");
                    setupAgentDetailsInitExpectations();
                    expect(request.getPathInfo()).andReturn(HttpTestUtil.VERSION_GET_REQUEST);
                    expect(request.getParameterMap()).andThrow(new UnsupportedOperationException(""));
                    expect(request.getAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE)).andReturn(null);
                    Vector<String> params = new Vector<>();
                    params.add("debug");
                    expect(request.getParameterNames()).andReturn(params.elements());
                    expect(request.getParameterValues("debug")).andReturn(new String[] {"false"});
                    expect(request.getAttribute("subject")).andReturn(null);
                    expect(request.getParameter(ConfigKey.STREAMING.getKeyValue())).andReturn(null);

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

        preparePostRequest(HttpTestUtil.VERSION_POST_REQUEST);

        replay(request, response);

        servlet.doPost(request, response);

        assertTrue(responseWriter.toString().contains(Version.getAgentVersion()));
        servlet.destroy();
    }

    @Test
    public void responseWithoutRequest() throws ServletException, IOException {
        prepareStandardInitialisation();

        ByteArrayOutputStream responseWriter = initRequestResponseMocks();
        expect(request.getCharacterEncoding()).andReturn("utf-8");
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn("text/plain");
        expect(request.getParameter(ConfigKey.INCLUDE_REQUEST.getKeyValue())).andReturn("false").anyTimes();
        expect(request.getAttribute("subject")).andReturn(null);

        preparePostRequest(HttpTestUtil.VERSION_POST_REQUEST);
        expect(request.getParameterMap()).andReturn(Map.of(
            ConfigKey.MIME_TYPE.getKeyValue(), new String[] { "text/plain" },
            ConfigKey.INCLUDE_REQUEST.getKeyValue(), new String[] { "false" })).anyTimes();

        replay(request, response);
        request.getParameterMap();

        servlet.doPost(request, response);

        assertFalse(responseWriter.toString().contains("\"type\":\"version\""));
        servlet.destroy();
    }

    @Test
    public void unknownMethodWhenSettingContentType() throws ServletException, IOException {
        prepareStandardInitialisation();

        ByteArrayOutputStream sw = initRequestResponseMocks(
                getStandardRequestSetup(),
                () -> {
                    response.setCharacterEncoding("utf-8");
                    expectLastCall().andThrow(new NoSuchMethodError());
                    response.setContentType("application/json; charset=utf-8");
                    response.setStatus(200);
                });
        expect(request.getPathInfo()).andReturn(HttpTestUtil.VERSION_GET_REQUEST);
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn(null);
        expect(request.getAttribute("subject")).andReturn(null);

        replay(request, response);

        servlet.doGet(request, response);

        assertTrue(sw.toString().contains(Version.getAgentVersion()));
        servlet.destroy();
    }

    @Test
    public void corsPreflightCheck() throws ServletException {
        checkCorsOriginPreflight("http://bla.com", "http://bla.com");
    }

    @Test
    public void corsPreflightCheckWithNullOrigin() throws ServletException {
        checkCorsOriginPreflight("null", "*");
    }

    private void checkCorsOriginPreflight(String in, String out) throws ServletException {
        prepareStandardInitialisation();
        request = createMock(HttpServletRequest.class);
        response = createMock(HttpServletResponse.class);

        expect(request.getHeader("Origin")).andReturn(in);
        expect(request.getHeader("Access-Control-Request-Headers")).andReturn(null);

        response.setHeader(eq("Access-Control-Max-Age"), anyObject());
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
                () -> {
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
                },
                () -> {
                    response.setHeader("Access-Control-Allow-Origin", out);
                    response.setHeader("Access-Control-Allow-Credentials","true");
                    response.setCharacterEncoding("utf-8");
                    response.setContentType("text/plain");
                    response.setStatus(200);
                }

        );
        expect(request.getPathInfo()).andReturn(HttpTestUtil.VERSION_GET_REQUEST);
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn("text/plain");
        replay(request, response);

        servlet.doGet(request, response);

        servlet.destroy();
    }

    private void setNoCacheHeaders(HttpServletResponse pResp) {
        pResp.setHeader("Cache-Control", "no-cache");
        pResp.setDateHeader(eq("Date"),anyLong());
        pResp.setDateHeader(eq("Expires"),anyLong());
    }

    @Test
    public void withCallback() throws IOException, ServletException {
        prepareStandardInitialisation();

        ByteArrayOutputStream sw = initRequestResponseMocks(
                "myCallback",
                getStandardRequestSetup(),
                () -> {
                    response.setCharacterEncoding("utf-8");
                    response.setContentType("text/javascript");
                    response.setStatus(200);
                });
        expect(request.getPathInfo()).andReturn(HttpTestUtil.VERSION_GET_REQUEST);
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
        expect(request.getPathInfo()).andReturn(HttpTestUtil.VERSION_GET_REQUEST);
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

        initConfigMocks(new String[] { ConfigKey.DEBUG.getKeyValue(), "true" }, null,"500", IllegalStateException.class);
        replay(config, context);
        servlet.init(config);
        ByteArrayOutputStream sw = initRequestResponseMocks(
                () -> {
                    expect(request.getScheme()).andStubReturn("http");
                    expect(request.getHeader("Origin")).andReturn(null);
                    expect(request.getRemoteAddr()).andThrow(new IllegalStateException());
                },
                getTextPlainResponseSetup());
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn("text/plain");

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

        ByteArrayOutputStream sw = initRequestResponseMocks(
            getStandardRequestSetup(),
            getStandardResponseSetup());
        expect(request.getPathInfo()).andReturn(HttpTestUtil.VERSION_GET_REQUEST);
        expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn(null);
        expect(request.getAttribute("subject")).andReturn(null);
        replay(request, response);

        servlet.doGet(request, response);

        assertTrue(sw.toString().contains(Version.getAgentVersion()));
        servlet.destroy();
    }


    @BeforeMethod
    void resetTestDetector() {
//        TestDetector.reset();
    }

    //@AfterMethod
    public void verifyMocks() {
//        verify(config, context, request, response);
    }
    // ============================================================================================

    private void initConfigMocks(String[] pInitParams, String[] pContextParams,String pLogRegexp, Class<? extends Exception> pExceptionClass) {
        config = createMock(ServletConfig.class);
        context = createMock(ServletContext.class);


        String[] params = prepareDebugLogging(pInitParams);
        HttpTestUtil.prepareServletConfigMock(config,params);
        HttpTestUtil.prepareServletContextMock(context, pContextParams);

        expect(config.getServletContext()).andStubReturn(context);
        expect(config.getServletName()).andStubReturn("jolokia");
        if (pExceptionClass != null) {
            context.log(find(pLogRegexp),isA(pExceptionClass));
        } else {
            if (pLogRegexp != null) {
                context.log(find(pLogRegexp));
            }
        }
        context.log(anyObject());
        EasyMock.expectLastCall().asStub();
        context.log(anyObject(),isA(JMException.class));
        expectLastCall().anyTimes();
    }

    private String[] prepareDebugLogging(String[] pInitParams) {
        if (pInitParams != null) {
            // If already set we do nothing
            for (int i = 0; i < pInitParams.length; i +=2) {
                if (ConfigKey.DEBUG.getKeyValue().equals(pInitParams[i])) {
                    return pInitParams;
                }
            }
        }
        // otherwise add debug config
        String[] params = pInitParams != null ? Arrays.copyOf(pInitParams, pInitParams.length + 2) : new String[2];
        params[params.length - 2] = ConfigKey.DEBUG.getKeyValue();
        params[params.length - 1] = DEBUG ? "true" : "false";
        return params;
    }

    private ByteArrayOutputStream initRequestResponseMocks() throws IOException {
        return initRequestResponseMocks(
                getStandardRequestSetup(),
                getTextPlainResponseSetup());
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
            public void write(int b) {
                baos.write(b);
            }

            public void setBaos(ByteArrayOutputStream baos){
                this.baos = baos;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
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
        return () -> {
            response.setCharacterEncoding("utf-8");
            // The default content type
            response.setContentType("application/json");
            response.setStatus(200);
        };
    }

    private Runnable getTextPlainResponseSetup() {
        return () -> {
            response.setCharacterEncoding("utf-8");
            // The default content type
            response.setContentType("text/plain");
            response.setStatus(200);
        };
    }

    private Runnable getStandardRequestSetup() {
        return () -> {
            expect(request.getScheme()).andStubReturn("http");
            expect(request.getHeader("Origin")).andStubReturn(null);
            expect(request.getHeader("Referer")).andStubReturn(null);
            expect(request.getRemoteHost()).andStubReturn("localhost");
            expect(request.getRemoteAddr()).andStubReturn("127.0.0.1");
            expect(request.getRequestURI()).andReturn("/jolokia/");
            setupAgentDetailsInitExpectations();
            expect(request.getParameterMap()).andReturn(null);
            expect(request.getAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE)).andReturn(null);
            expect(request.getParameter(ConfigKey.STREAMING.getKeyValue())).andReturn(null);

        };
    }

    private void setupAgentDetailsInitExpectations() {
        expect(request.getRequestURI()).andReturn("/jolokia/");
        expect(request.getRequestURL()).andReturn(new StringBuffer("http://localhost/jolokia"));
        expect(request.getContextPath()).andReturn("/jolokia");
        expect(request.getServletPath()).andReturn("");
        expect(request.getAuthType()).andReturn(null);
    }

    private Runnable getDiscoveryRequestSetup(final String url) {
        return () -> {
            expect(request.getScheme()).andStubReturn("http");
            expect(request.getHeader("Origin")).andStubReturn(null);
            expect(request.getHeader("Referer")).andStubReturn(null);
            expect(request.getRemoteHost()).andReturn("localhost");
            expect(request.getRemoteAddr()).andReturn("127.0.0.1");
            expect(request.getRequestURI()).andReturn("/jolokia/");
            expect(request.getParameterMap()).andReturn(null);
            expect(request.getAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE)).andReturn(null);

            expect(request.getPathInfo()).andReturn(HttpTestUtil.VERSION_GET_REQUEST);
            expect(request.getParameter(ConfigKey.MIME_TYPE.getKeyValue())).andReturn("text/plain").anyTimes();
            StringBuffer buf = new StringBuffer();
            buf.append(url).append(HttpTestUtil.VERSION_GET_REQUEST);
            expect(request.getRequestURL()).andReturn(buf);
            expect(request.getRequestURI()).andReturn("/jolokia" + HttpTestUtil.VERSION_GET_REQUEST);
            expect(request.getContextPath()).andReturn("/jolokia");
            expect(request.getServletPath()).andReturn("");
            expect(request.getAuthType()).andReturn("BASIC");
            expect(request.getAttribute("subject")).andReturn(null);
            expect(request.getParameter(ConfigKey.STREAMING.getKeyValue())).andReturn(null);

        };
    }


}
