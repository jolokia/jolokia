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
package org.jolokia.converter.json.simplifier;

import org.w3c.dom.Element;

/**
 * Special deserialization for DOM Elements to shorten the info
 *
 * @author roland
 * @since Jul 27, 2009
 */
public class DomElementSimplifier extends SimplifierAccessor<Element> {

    /**
     * Construct the simplifier for DOM elements
     */
    public DomElementSimplifier() {
        super(Element.class);

        addExtractor("name", new NameAttributeExtractor());
        addExtractor("namespace", new NamespaceAttributeExtractor());
        addExtractor("value", new ValueAttributeExtractor());
        addExtractor("hasChildNodes", new ChildAttributeExtractor());
    }

    @Override
    public String extractString(Object pValue) {
        Element el = (Element) pValue;
        String ns = el.getNamespaceURI();
        String localName = el.getNodeName();
        return ns == null || ns.isBlank() ? localName : "{" + ns + "}" + localName;
    }

    private static class ValueAttributeExtractor implements AttributeExtractor<Element> {
        @Override
        public Object extract(Element element) {
            return element.getNodeValue();
        }
    }

    private static class NameAttributeExtractor implements AttributeExtractor<Element> {
        @Override
        public Object extract(Element element) {
            return element.getNodeName();
        }
    }

    private static class NamespaceAttributeExtractor implements AttributeExtractor<Element> {
        @Override
        public Object extract(Element element) throws SkipAttributeException {
            String ns = element.getNamespaceURI();
            if (ns == null) {
                throw new SkipAttributeException();
            }
            return ns;
        }
    }

    private static class ChildAttributeExtractor implements AttributeExtractor<Element> {
        @Override
        public Object extract(Element element) {
            return element.hasChildNodes();
        }
    }

}
