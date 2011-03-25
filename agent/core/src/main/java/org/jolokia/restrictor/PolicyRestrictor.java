package org.jolokia.restrictor;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jolokia.request.RequestType;
import org.jolokia.util.HttpMethod;
import org.jolokia.util.IpChecker;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/*
 *  Copyright 2009-2010 Roland Huss
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


/**
 * Restrictor, which is based on a policy file
 *
 * @author roland
 * @since Jul 28, 2009
 */
public class PolicyRestrictor implements Restrictor {

    private Set<RequestType> typeSet;

    private Set<String> httpMethodsSet;

    private Set<String> allowedHostsSet;
    private Set<String> allowedSubnetsSet;

    // Simple patterns, could be mor specific
    private static final Pattern IP_PATTERN = Pattern.compile("^[\\d.]+$");
    private static final Pattern SUBNET_PATTERN = Pattern.compile("^[\\d.]+/[\\d.]+$");

    // Configuration for allowed and denied MBean attributes and operations.
    private MBeanPolicyConfig allow;
    private MBeanPolicyConfig deny;

    public PolicyRestrictor(InputStream pInput) {
        Exception exp = null;
        if (pInput == null) {
            throw new SecurityException("No policy file given");
        }
        try {
            Document doc =
                    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pInput);
            initTypeSet(doc);
            initHttpMethodSet(doc);
            initMBeanSets(doc);
            initAllowedHosts(doc);
        }
        catch (SAXException e) { exp = e; }
        catch (IOException e) { exp = e; }
        catch (ParserConfigurationException e) { exp = e; }
        catch (MalformedObjectNameException e) { exp = e; }
        finally {
            if (exp != null) {
                throw new SecurityException("Cannot parse policy file: " + exp,exp);
            }
        }
    }

    // ===============================================================================
    // Lookup methods

    public boolean isHttpMethodAllowed(HttpMethod method) {
        return httpMethodsSet == null || httpMethodsSet.contains(method.getMethod());
    }

    public boolean isTypeAllowed(String pType) {
        return typeSet == null || typeSet.contains(RequestType.getTypeByName(pType));
    }

    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return check(RequestType.READ,pName,pAttribute);
    }

    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return check(RequestType.WRITE,pName, pAttribute);
    }

    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return check(RequestType.EXEC,pName, pOperation);
    }

    private boolean check(RequestType pType, ObjectName pName, String pValue) {
        if (isTypeAllowed(pType.getName())) {
            // Its allowed in general, so we only need to check
            // the denied section, whether its forbidded
            return deny == null || !matches(deny, pType, pName, pValue);
        } else {
            // Its forbidden by default, so we need to check the
            // allowed section
            return allow != null && matches(allow, pType, pName, pValue);
        }
    }

    public boolean isRemoteAccessAllowed(String ... pHostOrAddress) {
        if (allowedHostsSet == null) {
            return true;
        }
        for (String addr : pHostOrAddress) {
            if (allowedHostsSet.contains(addr)) {
                return true;
            }
            if (allowedSubnetsSet != null && IP_PATTERN.matcher(addr).matches()) {
                for (String subnet : allowedSubnetsSet) {
                    if (IpChecker.matches(subnet, addr)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ===============================================================================
    // Lookup methods
    private boolean matches(MBeanPolicyConfig pConfig, RequestType pType, ObjectName pName, String pValue) {
        Set<String> values = pConfig.getValues(pType,pName);
        if (values == null) {
            ObjectName pattern = pConfig.findMatchingMBeanPattern(pName);
            if (pattern != null) {
                values = pConfig.getValues(pType,pattern);
            }
        }
        return values != null && (values.contains(pValue) || wildcardMatch(values,pValue));
    }

    // Check whether a value matches patterns in pValues
    private boolean wildcardMatch(Set<String> pValues, String pValue) {
        for (String pattern : pValues) {
            if (pattern.contains("*") && pValue.matches(pattern.replaceAll("\\*",".*"))) {
                return true;
            }
        }
        return false;
    }


    // ===============================================================================
    // Parsing routines
    private void initTypeSet(Document pDoc) {
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

    private void initHttpMethodSet(Document pDoc) {
        NodeList nodes = pDoc.getElementsByTagName("http");
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
                if (!methodName.equals("post") || methodName.equals("get")) {
                    throw new SecurityException("HTTP method must be either GET or POST, but not " + methodName);
                }
                httpMethodsSet.add(methodName);
            }
        }
    }

    private void initMBeanSets(Document pDoc) throws MalformedObjectNameException {
        for (String tag : new String[] { "allow", "mbeans" }) {
            NodeList nodes = pDoc.getElementsByTagName(tag);
            if (nodes.getLength() > 0) {
                // "allow" and "mbeans" are synonyms
                if (allow == null) {
                    allow = new MBeanPolicyConfig();
                }
                extractMbeanConfiguration(nodes, allow);
            }
        }
        NodeList nodes = pDoc.getElementsByTagName("deny");
        if (nodes.getLength() > 0) {
            deny = new MBeanPolicyConfig();
            extractMbeanConfiguration(nodes,deny);
        }
    }

    // Extract configuration and put it into a given MBeanPolicyConfig
    private void extractMbeanConfiguration(NodeList pNodes,MBeanPolicyConfig pConfig) throws MalformedObjectNameException {
        for (int i = 0;i< pNodes.getLength();i++) {
            Node node = pNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            extractPolicyConfig(pConfig, node.getChildNodes());
        }
    }

    private void extractPolicyConfig(MBeanPolicyConfig pConfig, NodeList pChilds) throws MalformedObjectNameException {
        for (int j = 0;j< pChilds.getLength();j++) {
            Node mBeanNode = pChilds.item(j);
            if (mBeanNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            assertNodeName(mBeanNode,"mbean");
            extractMBeanPolicy(pConfig, mBeanNode);
        }
    }

    private void extractMBeanPolicy(MBeanPolicyConfig pConfig, Node pMBeanNode) throws MalformedObjectNameException {
        NodeList params = pMBeanNode.getChildNodes();
        String name = null;
        Set<String> readAttributes = new HashSet<String>();
        Set<String> writeAttributes = new HashSet<String>();
        Set<String> operations = new HashSet<String>();
        for (int k = 0; k < params.getLength(); k++) {
            Node param = params.item(k);
            if (param.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            assertNodeName(param,"name","attribute","operation");
            String tag = param.getNodeName();
            if (tag.equals("name")) {
                if (name != null) {
                    throw new SecurityException("<name> given twice as MBean name");
                } else {
                    name = param.getTextContent().trim();
                }
            } else if (tag.equals("attribute")) {
                extractAttribute(readAttributes, writeAttributes, param);
            } else if (tag.equals("operation")) {
                operations.add(param.getTextContent().trim());
            } else {
                throw new SecurityException("Tag <" + tag + "> invalid");
            }
        }
        if (name == null) {
            throw new SecurityException("No <name> given for <mbean>");
        }
        pConfig.addValues(new ObjectName(name),readAttributes,writeAttributes,operations);
    }

    private void extractAttribute(Set<String> pReadAttributes, Set<String> pWriteAttributes, Node pParam) {
        Node mode = pParam.getAttributes().getNamedItem("mode");
        pReadAttributes.add(pParam.getTextContent().trim());
        if (mode == null || !mode.getNodeValue().equalsIgnoreCase("read")) {
            pWriteAttributes.add(pParam.getTextContent().trim());
        }
    }

    private void initAllowedHosts(Document pDoc) {
        NodeList nodes = pDoc.getElementsByTagName("remote");
        if (nodes.getLength() == 0) {
            // No restrictions found
            allowedHostsSet = null;
            return;
        }

        allowedHostsSet = new HashSet<String>();
        for (int i = 0;i<nodes.getLength();i++) {
            Node node = nodes.item(i);
            NodeList childs = node.getChildNodes();
            for (int j = 0;j<childs.getLength();j++) {
                Node hostNode = childs.item(j);
                if (hostNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                assertNodeName(hostNode,"host");
                String host = hostNode.getTextContent().trim().toLowerCase();
                if (SUBNET_PATTERN.matcher(host).matches()) {
                    if (allowedSubnetsSet == null) {
                        allowedSubnetsSet = new HashSet<String>();
                    }
                    allowedSubnetsSet.add(host);
                } else {
                    allowedHostsSet.add(host);
                }
            }
        }
    }

    private void assertNodeName(Node pNode, String ... pExpected) {
        for (String expected : pExpected) {
            if (pNode.getNodeName().equals(expected)) {
                return;
            }
        }
        StringBuffer buffer = new StringBuffer();
        for (int i=0; i < pExpected.length; i++) {
            buffer.append(pExpected[i]);
            if (i < pExpected.length-1) {
                buffer.append(",");
            }
        }
        throw new SecurityException(
                "Expected element " + buffer.toString() + " but got " + pNode.getNodeName());
    }


    // ====================================================================================================

    // Class combining various maps for attributes, operations and name patterns
    private static class MBeanPolicyConfig {
        private Set<ObjectName> patterns = new HashSet<ObjectName>();
        private Map<ObjectName,Set<String>> readAttributes = new HashMap<ObjectName, Set<String>>();
        private Map<ObjectName,Set<String>> writeAttributes = new HashMap<ObjectName, Set<String>>();
        private Map<ObjectName,Set<String>> operations = new HashMap<ObjectName, Set<String>>();

        public void addPattern(ObjectName pObjectName) {
            patterns.add(pObjectName);
        }

        void addValues(ObjectName pOName, Set<String> pReadAttributes, Set<String> pWriteAttributes, Set<String> pOperations) {
            readAttributes.put(pOName,pReadAttributes);
            writeAttributes.put(pOName,pWriteAttributes);
            operations.put(pOName,pOperations);
            if (pOName.isPattern()) {
                addPattern(pOName);
            }
        }

        Set<String> getValues(RequestType pType, ObjectName pName) {
            if (RequestType.READ == pType) {
                return readAttributes.get(pName);
            } else if (RequestType.WRITE == pType) {
                return writeAttributes.get(pName);
            } else if (RequestType.EXEC == pType) {
                return operations.get(pName);
            } else {
                throw new IllegalArgumentException("Invalid type " + pType);
            }
        }

        ObjectName findMatchingMBeanPattern(ObjectName pName) {
            // Check all stored patterns for a match and return the pattern if one is found
            for (ObjectName pattern : patterns) {
                if (pattern.apply(pName)) {
                    return pattern;
                }
            }
            return null;
        }
    }
}
