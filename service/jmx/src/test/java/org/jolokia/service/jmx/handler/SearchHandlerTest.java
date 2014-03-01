package org.jolokia.service.jmx.handler;

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

import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.JolokiaRequestBuilder;
import org.jolokia.server.core.request.JolokiaSearchRequest;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 12.09.11
 */
public class SearchHandlerTest extends BaseHandlerTest {


    private SearchHandler handler;
    private SearchHandler handlerWithRealm;

    private MBeanServer server;

    private TestJolokiaContext ctx;

    @BeforeMethod
    public void createHandler() throws MalformedObjectNameException {
        ctx = new TestJolokiaContext();
        handler = new SearchHandler(ctx, null);
        handlerWithRealm = new SearchHandler(ctx,"proxy");
    }


    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void unsupported() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, MalformedObjectNameException, NotChangedException {
        handler.handleRequest(null,
                              new JolokiaRequestBuilder(RequestType.SEARCH, "java.lang:*").<JolokiaSearchRequest>build());
    }

    @Test
    public void handleAllServersAtOnce() throws MalformedObjectNameException {
        assertTrue(handler.handleAllServersAtOnce(new JolokiaRequestBuilder(RequestType.SEARCH, "java.lang:*").<JolokiaSearchRequest>build()));
    }

    @Test
    public void simple() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        String names[] = { "java.lang:type=Memory", "java.lang:type=Runtime" };
        List<String> res = doSearch(handler, "java.lang:*", null, null, names);
        verifyResult(res, Collections.EMPTY_LIST, null, names);
        verify(server);
    }


    @Test
    public void withEscaping() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        String[] attrs = { "java.lang:type=\"m:e*m\\\"?o\\\\y\\n\"" };
        checkAllHandlers("java.lang:*", null, attrs);
        verify(server);
    }

    private void checkAllHandlers(String pSearch, Boolean pCanonical, String[] pNames) throws MalformedObjectNameException, NotChangedException, ReflectionException, IOException, InstanceNotFoundException, AttributeNotFoundException, MBeanException {
        for (SearchHandler h : new SearchHandler[] { handler, handlerWithRealm }) {
            List<String> previousResults = new ArrayList<String>(Arrays.asList("jolokia:type=dummy", "bla:name=blub"));
            List<String> res = doSearch(h, pSearch, pCanonical, new ArrayList(previousResults), pNames);
            verifyResult(res, previousResults, h == handlerWithRealm ? "proxy" : null, pNames);
        }
    }

    private void verifyResult(List<String> pRes, List<String> pPrev, String pRealm, String[] pNames) {
        assertEquals(pRes.size(), pNames.length  + pPrev.size());
        for (String name : pNames) {
            assertTrue(pRes.contains(pRealm != null ? pRealm + "@" + name : name));
        }
        for (String name : pPrev) {
            assertTrue(pRes.contains(name));
        }
    }

    @Test
    public void canonical() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        String namesIn[] = { "java.lang:type=Memory,name=bla", "java.lang:type=Runtime,mode=run" };
        String namesOut[] = { "java.lang:name=bla,type=Memory", "java.lang:mode=run,type=Runtime" };
        List<String> res = doSearch(handler, "java.lang:*", Boolean.TRUE, null, namesIn);
        verifyResult(res, Collections.EMPTY_LIST, null, namesOut);
        verify(server);
    }

    @Test
    public void constructionTime() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        String names[] = { "java.lang:type=Memory,name=bla", "java.lang:type=Runtime,mode=run" };
        checkAllHandlers("java.lang:*",Boolean.FALSE,names);
        verify(server);
    }

    private List<String> doSearch(SearchHandler pHandler, String pPattern, Boolean pUseCanonicalName, List<String> previousResult, String ... pFoundNames) throws MalformedObjectNameException, IOException, InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, NotChangedException {
        ObjectName oName = new ObjectName(pPattern);
        JolokiaRequestBuilder builder = new JolokiaRequestBuilder(RequestType.SEARCH,oName);
        if (pUseCanonicalName != null) {
            builder.option(ConfigKey.CANONICAL_NAMING,pUseCanonicalName.toString());
        }
        JolokiaSearchRequest request = builder.build();

        server = createMock(MBeanServer.class);
        Set<ObjectName> names = new HashSet<ObjectName>();
        for (String name : pFoundNames) {
            names.add(new ObjectName(name));
        }
        expect(server.queryNames(oName,null)).andReturn(names);
        replay(server);
        return (List<String>) pHandler.handleRequest(getMBeanServerManager(server),request, previousResult);
    }
}
