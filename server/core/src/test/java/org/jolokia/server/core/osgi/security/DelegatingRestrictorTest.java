package org.jolokia.server.core.osgi.security;

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

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.util.HttpMethod;
import org.jolokia.server.core.util.RequestType;
import org.osgi.framework.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 12.09.11
 */
public class DelegatingRestrictorTest {

    private DelegatingRestrictor restrictor;
    private BundleContext context;

    @BeforeMethod
    public void setup() throws InvalidSyntaxException {
        context = createMock(BundleContext.class);
        restrictor = new DelegatingRestrictor(context);
    }

    private void setupRestrictor(Restrictor pRestrictor) throws InvalidSyntaxException {
        ServiceReference<?>[] refs;
        if (pRestrictor != null) {
            ServiceReference<Restrictor> ref = createMock(ServiceReference.class);
            refs = new ServiceReference[] { ref };
            expect(context.getService(ref)).andStubReturn(pRestrictor);
            expect(context.ungetService(ref)).andStubReturn(true);
            replay((Object[]) refs);
        } else {
            refs = null;
        }
        expect(context.getServiceReferences("org.jolokia.server.core.service.api.Restrictor", null)).andReturn(refs).anyTimes();
        replay(context);
    }

    @Test
    public void nullRestrictor() throws InvalidSyntaxException, MalformedObjectNameException {
        setupRestrictor(null);
        for (HttpMethod method : HttpMethod.values()) {
            assertFalse(restrictor.isHttpMethodAllowed(method));
        }
        for (RequestType type : RequestType.values()) {
            assertFalse(restrictor.isTypeAllowed(type));
        }
        assertFalse(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"), "HeapMemoryUsage"));
        assertFalse(restrictor.isAttributeWriteAllowed(new ObjectName("java.lang:type=Memory"), "HeapMemoryUsage"));
        assertFalse(restrictor.isOperationAllowed(new ObjectName("java.lang:type=Memory"), "gc"));
        assertFalse(restrictor.isRemoteAccessAllowed("localhost", "127.0.0.1"));
    }


    @Test
    public void withRestrictor() throws InvalidSyntaxException, MalformedObjectNameException {
        setupRestrictor(new InnerRestrictor(true,false,true,false,true,false,true,false));
        assertTrue(restrictor.isHttpMethodAllowed(HttpMethod.GET));
        assertFalse(restrictor.isTypeAllowed(RequestType.EXEC));
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"), "HeapMemoryUsage"));
        assertFalse(restrictor.isAttributeWriteAllowed(new ObjectName("java.lang:type=Memory"), "HeapMemoryUsage"));
        assertTrue(restrictor.isOperationAllowed(new ObjectName("java.lang:type=Memory"), "gc"));
        assertFalse(restrictor.isRemoteAccessAllowed("localhost", "127.0.0.1"));
        assertTrue(restrictor.isOriginAllowed("http://bla.com", false));
        assertFalse(restrictor.isObjectNameHidden(new ObjectName("java.lang:type=Memory")));
        assertEquals(restrictor.restrictedAttributeValue(new ObjectName("java.lang:type=Memory"),
            "HeapMemoryUsage", "70%"), "*****");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*Impossible.*")
    public void invalidSyntax() throws InvalidSyntaxException {
        expect(context.getServiceReferences("org.jolokia.server.core.service.api.Restrictor", null)).andThrow(new InvalidSyntaxException("", null));
        replay(context);
        restrictor.isHttpMethodAllowed(HttpMethod.GET);
    }

    private static class InnerRestrictor implements Restrictor {

        boolean httpMethod,type,read,write,operation,remote,cors,isHidden;

        private InnerRestrictor(boolean pHttpMethod, boolean pType, boolean pRead, boolean pWrite, boolean pOperation, boolean pRemote, boolean pCors, boolean pIsHidden) {
            httpMethod = pHttpMethod;
            type = pType;
            read = pRead;
            write = pWrite;
            operation = pOperation;
            remote = pRemote;
            cors = pCors;
            isHidden = pIsHidden;
        }


        public boolean isHttpMethodAllowed(HttpMethod pMethod) {
            return httpMethod;
        }

        public boolean isTypeAllowed(RequestType pType) {
            return type;
        }

        public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
            return read;
        }

        public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
            return write;
        }

        public boolean isOperationAllowed(ObjectName pName, String pOperation) {
            return operation;
        }

        public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
            return remote;
        }

        public boolean isOriginAllowed(String pOrigin, boolean pOnlyWhenStrictCheckingIsEnabled) {
            return cors;
        }

        public boolean isObjectNameHidden(ObjectName name) {
            return isHidden;
        }

        public Object restrictedAttributeValue(ObjectName pName, String pAttribute, Object object) {
            if("HeapMemoryUsage".equals(pAttribute)) {
                return "*****";
            }
            return object;
        }
    }
}
