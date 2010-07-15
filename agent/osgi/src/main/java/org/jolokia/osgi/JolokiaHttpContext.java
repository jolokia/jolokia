package org.jolokia.osgi;

import org.osgi.service.http.HttpContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;

/**
 * Basic JolokiaHttpContextContext
 *
 * @author roland
 * @since Jan 7, 2010
 */
class JolokiaHttpContext implements HttpContext {
    public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return true;
    }

    public URL getResource(String name) {
        return null;
    }

    public String getMimeType(String name) {
        return null;
    }
}
