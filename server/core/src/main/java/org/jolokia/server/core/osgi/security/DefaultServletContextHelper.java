package org.jolokia.server.core.osgi.security;

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

import java.net.URL;
import java.util.Set;

import org.osgi.service.servlet.context.ServletContextHelper;

/**
 * Basic {@link ServletContextHelper}, which does no authentication.
 *
 * @author roland
 * @since Jan 7, 2010
 */
public class DefaultServletContextHelper extends ServletContextHelper {

    /**
     * Always return null.
     *
     * {@inheritDoc}
     */
    @Override
    public URL getResource(String name) {
        return null;
    }

    /**
     * Always return null.
     *
     * {@inheritDoc}
     */
    @Override
    public Set<String> getResourcePaths(String path) {
        return null;
    }

}
