/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.support.spring.actuator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jolokia.core.util.ClassUtil;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.Configuration;
import org.jolokia.server.core.restrictor.RestrictorFactory;
import org.jolokia.server.core.restrictor.policy.PolicyRestrictor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Converts Spring CORS properties to the existing XML policy implementation. */
final class JolokiaCorsPolicy {

    private static final List<String> OPTIONS = List.of("allow-origin", "strict-checking", "ignore-scheme");

    private JolokiaCorsPolicy() {
    }

    static boolean isConfigured(Map<String, String> config) {
        return OPTIONS.stream().anyMatch(config::containsKey)
            || config.keySet().stream().anyMatch(JolokiaCorsPolicy::isIndexedOption);
    }

    private static boolean isIndexedOption(String key) {
        return OPTIONS.stream().anyMatch(option -> key.startsWith(option + "[") || key.startsWith(option + "."));
    }

    private static boolean booleanOption(Map<String, String> config, String key) {
        if (!config.containsKey(key)) {
            return false;
        }
        String value = config.get(key);
        if (value != null) {
            if ("true".equalsIgnoreCase(value.trim())) {
                return true;
            }
            if ("false".equalsIgnoreCase(value.trim())) {
                return false;
            }
        }
        throw new IllegalArgumentException("management.endpoint.jolokia.config." + key + " must be true or false");
    }

    static PolicyRestrictor create(Map<String, String> config, Configuration configuration) {
        if (config.keySet().stream().anyMatch(JolokiaCorsPolicy::isIndexedOption)) {
            throw new IllegalArgumentException("management.endpoint.jolokia.config CORS options must be scalar values; use a comma-separated string for allow-origin");
        }
        boolean strictChecking = booleanOption(config, "strict-checking");
        boolean ignoreScheme = booleanOption(config, "ignore-scheme");
        String origins = config.getOrDefault("allow-origin", "");
        if (origins == null) {
            throw new IllegalArgumentException("management.endpoint.jolokia.config.allow-origin must not be null");
        }
        List<String> allowedOrigins = origins.isBlank() ? List.of()
            : Arrays.stream(origins.split(",", -1)).map(String::trim).toList();
        if (allowedOrigins.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("management.endpoint.jolokia.config.allow-origin must not contain empty entries");
        }
        if (configuration.containsKey(ConfigKey.RESTRICTOR_CLASS)
                || configuration.containsKey(ConfigKey.POLICY_LOCATION)) {
            throw new IllegalStateException("management.endpoint.jolokia.config CORS settings cannot be combined with "
                + "restrictorClass or policyLocation");
        }
        // Use the same resource lookup as RestrictorFactory, without parsing or fetching a remote policy.
        String path = ConfigKey.POLICY_LOCATION.getDefaultValue().substring("classpath:".length());
        InputStream resource = ClassUtil.getResourceAsStream(path);
        if (resource == null) {
            resource = RestrictorFactory.class.getResourceAsStream(path);
        }
        try (InputStream policy = resource) {
            if (policy != null) {
                throw new IllegalStateException("management.endpoint.jolokia.config CORS settings cannot be combined with "
                    + "classpath:/jolokia-access.xml");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot check the existing Jolokia policy", e);
        }

        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element access = document.createElement("access");
            document.appendChild(access);
            Element element = document.createElement("cors");
            access.appendChild(element);
            for (String origin : allowedOrigins) {
                Element allowed = document.createElement("allow-origin");
                allowed.setTextContent(origin);
                element.appendChild(allowed);
            }
            // XML switches are enabled by element presence, not by their text content.
            if (strictChecking) {
                element.appendChild(document.createElement("strict-checking"));
            }
            if (ignoreScheme) {
                element.appendChild(document.createElement("ignore-scheme"));
            }
            ByteArrayOutputStream xml = new ByteArrayOutputStream();
            TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(xml));
            return new PolicyRestrictor(new ByteArrayInputStream(xml.toByteArray()));
        } catch (ParserConfigurationException | TransformerException e) {
            throw new IllegalStateException("Cannot create Jolokia CORS policy", e);
        }
    }
}
