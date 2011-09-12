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

import org.jolokia.util.RequestType;
import org.w3c.dom.*;

/**
 * Checks for a certain requst type which are specified within a <code>&lt;commands&gt;</code>
 * section. If no such section is present, then all commands are allowed, otherwise only
 * the given type is allowed by this checker.
 * <p/>
 * Allowed types are the names as defined in {@link RequestType}.
 * <p/
 * Example:
 * <pre>
 * &lt;commands&gt;
 *   &lt;command&gt;read&lt;/command&gt;
 *   &lt;command&gt;list&lt;/command&gt;
 * &lt;/commands&gt;
 * </pre>
 * @author roland
 * @since 02.09.11
 */
public class RequestTypeChecker extends AbstractChecker<RequestType> {

    private Set<RequestType> typeSet;

    /**
     * Checker for requests types
     *
     * @param pDoc document to examine for declared restrictions
     */
    public RequestTypeChecker(Document pDoc) {
        NodeList nodes = pDoc.getElementsByTagName("commands");
        if (nodes.getLength() > 0) {
            // Leave typeSet null if no commands has been given...
            typeSet = new HashSet<RequestType>();
        }
        for (int i = 0;i<nodes.getLength();i++) {
            Node node = nodes.item(i);
            NodeList childs = node.getChildNodes();
            for (int j = 0;j<childs.getLength();j++) {
                Node commandNode = childs.item(j);
                if (commandNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                assertNodeName(commandNode,"command");
                String typeName = commandNode.getTextContent().trim();
                RequestType type = RequestType.getTypeByName(typeName);
                typeSet.add(type);
            }
        }
    }

    /**
     * Return true if either no <code>&lt;commands&gt;</code> section was in the policy or
     * the given type was mentioned as allowed command
     *
     * @param pType the type to check
     * @return true if the condition above is true
     */
    @Override
    public boolean check(RequestType pType) {
        return typeSet == null || typeSet.contains(pType);
    }
}
