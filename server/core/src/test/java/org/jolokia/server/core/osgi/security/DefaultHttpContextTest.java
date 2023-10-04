package org.jolokia.server.core.osgi.security;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class DefaultHttpContextTest {

    private DefaultServletContextHelper ctx;

    @BeforeMethod
    public void setUp() throws Exception {
        ctx = new DefaultServletContextHelper();
    }

    @Test
    public void testHandleSecurity() throws Exception {
        assertTrue(ctx.handleSecurity(null,null));
    }

    @Test
    public void testGetResource() {
        assertNull(ctx.getMimeType(null));
    }

    @Test
    public void testGetMimeType() {
        assertNull(ctx.getResource(null));
    }
}
