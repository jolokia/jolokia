package org.jolokia.converter.json.simplifier;

import java.net.URL;

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


/**
 * Simplifier for URLs which result in a map with a single key <code>url</code>
 *
 * @author roland
 * @since Jul 27, 2009
 */
public class UrlSimplifier extends SimplifierExtractor<URL> {

    /**
     * No arg constructor as required for simplifiers
     */
    public UrlSimplifier() {
        super(URL.class);
        addExtractor("url", new UrlAttributeExtractor());
    }

    private static class UrlAttributeExtractor implements AttributeExtractor<URL> {
        /** {@inheritDoc} */
        public Object extract(URL pUrl) { return pUrl.toExternalForm(); }
    }
}