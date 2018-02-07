package org.jolokia.osgi.security;

import java.io.IOException;
import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isNull;
import static org.easymock.EasyMock.replay;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * @author roland
 * @since 07.02.18
 */
public class ServiceAuthenticationHttpContextTest {


    @Test
    public void checkEmptyAny() throws Exception {
        checkEmpty(ServiceAuthenticationHttpContext.AUTHMODE_SERVICE_ANY);
    }

    @Test
    public void checkEmptyAll() throws Exception {
        checkEmpty(ServiceAuthenticationHttpContext.AUTHMODE_SERVICE_ALL);
    }

    @Test
    public void checkEmptyInvalid() throws Exception {
        try {
            checkEmpty("bla");
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("bla"));
        }
    }

    private void checkEmpty(String mode) throws IOException, InvalidSyntaxException {
        BundleContext bundleContext = createBundleContext();
        expect(bundleContext.getServiceReferences(eq(Authenticator.class.getName()), (String) isNull())).andReturn(null);
        replay(bundleContext);
        ServiceAuthenticationHttpContext ctx = createContext(bundleContext, mode);
        HttpServletResponse resp = prepareResponse();
        assertFalse(ctx.handleSecurity(null, resp));
    }

    @Test
    public void anyAuthenticator() throws Exception {
        Object[] testData = new Object[]{
            "service-any", new boolean[]{true, false}, true,
            "service-all", new boolean[]{true, false}, false,
            "service-any", new boolean[]{true, true}, true,
            "service-all", new boolean[]{true, true}, true,
            "service-any", new boolean[]{false, false}, false,
            "service-all", new boolean[]{false, false}, false,
            "service-any", new boolean[]{false, true}, true,
            "service-all", new boolean[]{false, true}, false,
            "service-all", new boolean[]{true, true, true, true, false, true}, false,
            "service-any", new boolean[]{false, false, false, false, false, true}, true
        };

        for (int i = 0; i < testData.length; i += 3) {
            BundleContext bundleContext = createBundleContext();
            ServiceReference[] serviceRefs = createServiceReferences(bundleContext, (boolean[]) testData[i + 1]);

            expect(bundleContext.getServiceReferences(eq(Authenticator.class.getName()), (String) isNull())).andReturn(serviceRefs);
            replay(bundleContext);

            ServiceAuthenticationHttpContext ctx = createContext(bundleContext, (String) testData[i]);
            HttpServletResponse resp = prepareResponse();
            assertEquals(ctx.handleSecurity(null, resp), testData[i + 2],
                         String.format("%s: %s --> %s", testData[i], printBooleanList((boolean[]) testData[i + 1]), testData[i + 2]));
        }
    }

    private BundleContext createBundleContext() throws InvalidSyntaxException {
        BundleContext bundleContext = createMock(BundleContext.class);
        expect(bundleContext.createFilter((String) anyObject())).andReturn(null);
        bundleContext.addServiceListener((ServiceListener) anyObject(), (String) anyObject());
        return bundleContext;
    }

    @Test
    public void checkClose() throws Exception {
        BundleContext bundleContext = createBundleContext();
        expect(bundleContext.getServiceReferences(eq(Authenticator.class.getName()), (String) isNull())).andReturn(null);
        bundleContext.removeServiceListener((ServiceListener) anyObject());
        replay(bundleContext);
        ServiceAuthenticationHttpContext ctx = createContext(bundleContext, "service-any");
        ctx.close();
    }

    private String printBooleanList(boolean[] authValues) {
        StringBuffer ret = new StringBuffer();
        for (boolean authValue : authValues) {
            ret.append(authValue).append("::");
        }
        return ret.toString().substring(0,ret.length()-2);
    }

    private ServiceReference[] createServiceReferences(BundleContext bundleContext, boolean ... authResults) throws InvalidSyntaxException {
        ArrayList<ServiceReference> ret = new ArrayList<ServiceReference>();
        for (boolean authResult : authResults) {
            ServiceReference ref = createMock(ServiceReference.class);
            Authenticator auth = new TestAuthenticator(authResult);
            expect(bundleContext.getService(eq(ref))).andReturn(auth);
            replay(ref);
            ret.add(ref);
        }
        return ret.toArray(new ServiceReference[0]);
    }

    private HttpServletResponse prepareResponse() {
        return createMock(HttpServletResponse.class);
    }

    private ServiceAuthenticationHttpContext createContext(BundleContext bundleContext, String authMode) {
        return new ServiceAuthenticationHttpContext(bundleContext,authMode);
    }

    // ===================================================================================

    public static class TestAuthenticator implements Authenticator {

        boolean pass;

        TestAuthenticator(boolean pass) {
            this.pass = pass;
        }

        @Override
        public boolean authenticate(HttpServletRequest pRequest) {
            return pass;
        }

    }
}
