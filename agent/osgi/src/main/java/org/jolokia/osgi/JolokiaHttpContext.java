package org.jolokia.osgi;

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

import org.osgi.service.http.HttpContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;

/**
 * Basic JolokiaHttpContextContext, which does no authentication.
 *
 * @author roland
 * @since Jan 7, 2010
 */
class JolokiaHttpContext implements HttpContext {

    /**
     * This metod always returns true and does not handle security
     *
     * {@inheritDoc}
     */
    public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return true;
    }

    /**
     * Always return null.
     *
     * {@inheritDoc}
     */
    public URL getResource(String name) {
        return null;
    }

    /**
     * Always return null
     * {@inheritDoc}
     *
     */
    public String getMimeType(String name) {
        return null;
    }
}
