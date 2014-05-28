package org.jolokia.converter.json.simplifier;

import org.w3c.dom.Element;

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
 * Special deserialization for DOM Elements to shorten the info
 *
 * @author roland
 * @since Jul 27, 2009
 */
public class DomElementSimplifier extends SimplifierExtractor<Element> {

    /**
     * Construct the simplifier for DOM elements
     */
    public DomElementSimplifier() {
        super(Element.class);

        Object[][] pAttrs = {
                { "name", new NameAttributeExtractor() },
                { "value", new ValueAttributeExtractor() },
                { "hasChildNodes", new ChildAttributeExtractor() }
        };
        addExtractors(pAttrs);
    }

    // ==================================================================================
    private static class ValueAttributeExtractor implements AttributeExtractor<Element> {
       /** {@inheritDoc} */
        public Object extract(Element element) { return element.getNodeValue(); }
    }
    private static class NameAttributeExtractor implements AttributeExtractor<Element> {
        /** {@inheritDoc} */
        public Object extract(Element element) { return element.getNodeName(); }
    }
    private static class ChildAttributeExtractor implements AttributeExtractor<Element> {
        /** {@inheritDoc} */
        public Object extract(Element element) { return element.hasChildNodes(); }
    }

}