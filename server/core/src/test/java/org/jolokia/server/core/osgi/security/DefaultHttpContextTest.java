package org.jolokia.server.core.osgi.security;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class DefaultHttpContextTest {

    private DefaultHttpContext ctx;

    @BeforeMethod
    public void setUp() throws Exception {
        ctx = new DefaultHttpContext();
    }

    @Test
    public void testHandleSecurity() throws Exception {
        assertTrue(ctx.handleSecurity(null,null));
    }

    @Test
    public void testGetResource() throws Exception {
        assertNull(ctx.getMimeType(null));
    }

    @Test
    public void testGetMimeType() throws Exception {
        assertNull(ctx.getResource(null));
    }
}