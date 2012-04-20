package org.jolokia.restrictor.policy;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.util.HashSet;
import java.util.Set;

import org.jolokia.util.HttpMethod;
import org.w3c.dom.*;

/**
 * Checker, which extracts <code>http</code> elements from the policy document which in turn
 * declares the allowed HTTP methods ("get" or "post")
 *
 * Example for allowing only post calls:
 *
 * <pre>
 *     &lt;http&gt;
 *        &lt;method&gt;post&lt;/method&gt;
 *     &lt;/http&gt;
 * </pre>
 * @author roland
 * @since 02.09.11
 */
public class HttpMethodChecker extends AbstractChecker<HttpMethod> {

    private Set<String> httpMethodsSet;

    /**
     * Create a checker which checks for certain HTTP methods
     *
     * @param pPolicy policy document
     */
    public HttpMethodChecker(Document pPolicy) {
        NodeList nodes = pPolicy.getElementsByTagName("http");
        if (nodes.getLength() > 0) {
            // Leave typeSet null if no commands has been given...
            httpMethodsSet = new HashSet<String>();
        }
        for (int i = 0;i<nodes.getLength();i++) {
            Node node = nodes.item(i);
            NodeList childs = node.getChildNodes();
            for (int j = 0;j<childs.getLength();j++) {
                Node commandNode = childs.item(j);
                if (commandNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                assertNodeName(commandNode,"method");
                String methodName = commandNode.getTextContent().trim().toLowerCase();
                if (!methodName.equals("post") && ! methodName.equals("get")) {
                    throw new SecurityException("HTTP method must be either GET or POST, but not " + methodName.toUpperCase());
                }
                httpMethodsSet.add(methodName);
            }
        }
    }

    @Override
    public boolean  check(HttpMethod pMethod) {
        return httpMethodsSet == null || httpMethodsSet.contains(pMethod.getMethod());
    }

}
