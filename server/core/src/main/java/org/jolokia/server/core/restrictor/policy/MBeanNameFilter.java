/*
 * Copyright 2009-2024 Roland Huss
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
package org.jolokia.server.core.restrictor.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A checker that is used to filter out results of {@code search} and {@code list} operations.
 * <em>Allowed</em> MBean is the one that's present (is not filtered out).
 */
public class MBeanNameFilter extends AbstractChecker<ObjectName> {

    private final List<Function<ObjectName, Boolean>> filters = new ArrayList<>();

    /**
     * Constructor buiilding up this checker from the XML document provided.
     * filter sections look like
     * <pre>
     *     &lt;filter&gt;
     *       &lt;mbean&gt;http://jolokia.org&lt;/mbean&gt;
     *       &lt;mbean&gt;*://*.jmx4perl.org&lt;/mbean&gt;
     *     &lt;/filter&gt;
     * </pre>
     *
     * @param pDoc the overall policy documents
     */
    public MBeanNameFilter(Document pDoc) {
        NodeList filteredNodes = pDoc.getElementsByTagName("filter");
        if (filteredNodes.getLength() > 0) {
            for (int i = 0; i < filteredNodes.getLength(); i++) {
                Node filteredNode = filteredNodes.item(i);
                NodeList nodes = filteredNode.getChildNodes();
                for (int j = 0;j <nodes.getLength();j++) {
                    Node node = nodes.item(j);
                    if (node.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    assertNodeName(node,"mbean");
                    String pattern = node.getTextContent().trim();
                    Function<ObjectName, Boolean> matcher = createMatcher(pattern);
                    if (matcher != null) {
                        filters.add(matcher);
                    }
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean check(ObjectName pArg) {
        return !isObjectNameHidden(pArg);
    }

    /**
     * Returns {@code true} if given {@link ObjectName} should be filtered out (not shown to the client)
     * @param name
     * @return
     */
    public boolean isObjectNameHidden(ObjectName name) {
        boolean hidden = false;
        for (Function<ObjectName, Boolean> filter : filters) {
            if (filter.apply(name)) {
                return true;
            }
        }

        return false;
    }

    private Function<ObjectName, Boolean> createMatcher(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }

        if (!pattern.contains(":") || (pattern.endsWith(":*") && pattern.indexOf(':') == pattern.length() - 2)) {
            // domain only
            if (pattern.endsWith(":*")) {
                pattern = pattern.substring(0, pattern.length() - 2);
            }
            return createDomainMatcher(pattern.trim());
        } else {
            // domain and set of ObjectName attributes
            try {
                ObjectName patternName = new ObjectName(pattern.trim());
                final List<Function<ObjectName, Boolean>> filters = new ArrayList<>();
                filters.add(createDomainMatcher(patternName.getDomain()));
                patternName.getKeyPropertyList().forEach((k, v) -> {
                    filters.add(createPropertyMatcher(k, v));
                });

                return new ComplexMatcher(filters);
            } catch (MalformedObjectNameException e) {
                throw new IllegalArgumentException("Invalid MBean name filter: \"" + pattern + "\"", e);
            }
        }
    }

    private DomainMatcher createDomainMatcher(String pattern) {
        validateSingleGlob(pattern);
        if (pattern.contains("*")) {
            // simple wildcard match
            String[] parts = pattern.split("\\*");
            String prefix = parts[0].isEmpty() ? null : parts[0];
            String suffix = parts.length == 1 || parts[1].isEmpty() ? null : parts[1];
            return new DomainMatcher(prefix, suffix);
        } else {
            // strict match
            return new DomainMatcher(pattern);
        }
    }

    private Function<ObjectName, Boolean> createPropertyMatcher(String key, String pattern) {
        if (key == null || key.contains("*")) {
            throw new IllegalArgumentException("Key can't contain wildcard: \"" + key + "\"");
        }
        validateSingleGlob(pattern);
        if (pattern.contains("*")) {
            // simple wildcard match
            String[] parts = pattern.split("\\*");
            String prefix = parts.length == 0 || parts[0].isEmpty() ? null : parts[0];
            String suffix = parts.length <= 1 || parts[1].isEmpty() ? null : parts[1];
            return new PropertyMatcher(key, prefix, suffix);
        } else {
            // strict match
            return new PropertyMatcher(key, pattern);
        }
    }

    private void validateSingleGlob(String pattern) {
        if (pattern.indexOf('*') != pattern.lastIndexOf('*')) {
            throw new IllegalArgumentException("Can't use multiple Glob patterns: \"" + pattern + "\"");
        }
    }

    private static class DomainMatcher implements Function<ObjectName, Boolean> {
        private final String domain;
        private final String prefix;
        private final String suffix;

        public DomainMatcher(String pattern) {
            this.domain = pattern;
            this.prefix = null;
            this.suffix = null;
        }
        public DomainMatcher(String prefix, String suffix) {
            this.domain = null;
            this.prefix = prefix;
            this.suffix = suffix;
        }

        @Override
        public Boolean apply(ObjectName objectName) {
            String d = objectName.getDomain();
            if (domain != null) {
                return d.equals(domain);
            } else {
                return (prefix == null || d.startsWith(prefix))
                    && (suffix == null || d.endsWith(suffix));
            }
        }
    }

    private static class PropertyMatcher implements Function<ObjectName, Boolean> {
        private final String key;
        private final String value;
        private final String prefix;
        private final String suffix;

        public PropertyMatcher(String key, String value) {
            this.key = key;
            this.value = value;
            this.prefix = null;
            this.suffix = null;
        }
        public PropertyMatcher(String key, String prefix, String suffix) {
            this.key = key;
            this.value = null;
            this.prefix = prefix;
            this.suffix = suffix;
        }

        @Override
        public Boolean apply(ObjectName objectName) {
            String propertyValue = objectName.getKeyProperty(key);
            if (propertyValue == null) {
                // no such property, no matching, objectName won't be hidden (by this matcher at least)
                return false;
            }
            if (value != null) {
                return propertyValue.equals(value);
            } else {
                return (prefix == null || propertyValue.startsWith(prefix))
                    && (suffix == null || propertyValue.endsWith(suffix));
            }
        }
    }

    private static class ComplexMatcher implements Function<ObjectName, Boolean> {
        private final List<Function<ObjectName, Boolean>> filters;

        public ComplexMatcher(List<Function<ObjectName, Boolean>> filters) {
            this.filters = filters;
        }

        @Override
        public Boolean apply(ObjectName objectName) {
            // matches if all match
            boolean match = true;
            for (Function<ObjectName, Boolean> filter : filters) {
                match &= filter.apply(objectName);
            }

            return match;
        }
    }

}
