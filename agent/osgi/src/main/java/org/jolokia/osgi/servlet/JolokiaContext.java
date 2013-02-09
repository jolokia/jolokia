package org.jolokia.osgi.servlet;

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

/**
 * Context exported as OSGi Service so that other bundles
 * can get access to configuration information of the
 * Jolokia agent.
 *
 * @author roland
 * @since 04.02.11
 */
public interface JolokiaContext {

    /**
     * Get the HttpService context used for authentication. A client can use
     * this context in order to use the same authentication as this Jolokia
     * handler
     *
     * @return HttpService context
     */
    HttpContext getHttpContext();

    /**
     * Get the context path (alias) under which the jolokia servlet  is registered
     *
     * @return the context (e.g. "/jolokia")
     */
    String getServletAlias();
}
