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

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.util.RequestType;
import org.w3c.dom.*;

/**
 * Checker, which checks for specific MBean attributes or operations which can be either
 * defined in an <code>&lt;allow&gt;</code> or <code>&lt;deny&gt;</code> seciton.
 * <p/>
 * MBean names can be specified either a full names or as patterns in which case the rule
 * applies to all MBeans matching this pattern. For attribute and operations names, the wildcard
 * <code>*</code> is allowed, too.
 * <p/>
 * Example:
 * <pre>
 * &lt;allow&gt;
 *    &lt;mbean&gt;
 *     &lt;name&gt;java.lang:type=Memory&lt;/name&gt;
 *     &lt;operation&gt;gc&lt;/operation&gt;
 *   &lt;/mbean&gt;
 * &lt;/allow&gt;
 *
 * &lt;deny&gt;
 *   &lt;mbean&gt;
 *     &lt;name&gt;com.mchange.v2.c3p0:type=PooledDataSource,*&lt;/name&gt;
 *     &lt;attribute&gt;properties&lt;/attribute&gt;
 *   &lt;/mbean&gt;
 * &lt;/deny&gt;
 * </pre>
 *
 * @author roland
 * @since 03.09.11
 */
public class MBeanAccessChecker extends AbstractChecker<MBeanAccessChecker.Arg> {

    // Configuration for allowed and denied MBean attributes and operations.
    private MBeanPolicyConfig allow;
    private MBeanPolicyConfig deny;

    /**
     * Constructor which extracts the information relevant for this checker from the given document.
     *
     * @param pDoc document to examine
     * @throws MalformedObjectNameException if the configuration contains malformed object names.
     */
    public MBeanAccessChecker(Document pDoc) throws MalformedObjectNameException {
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

    /** {@inheritDoc} */
    @Override
    public boolean check(Arg pArg) {
        if (pArg.isTypeAllowed()) {
            // Its allowed in general, so we only need to check
            // the denied section, whether its forbidded
            return deny == null || !matches(deny, pArg);
        } else {
            // Its forbidden by default, so we need to check the
            // allowed section
            return allow != null && matches(allow, pArg);
        }
    }

    // =======================================================================================

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

    // Lookup methods
    private boolean matches(MBeanPolicyConfig pConfig, Arg pArg) {

        Set<String> values = pConfig.getValues(pArg.getType(),pArg.getName());
        if (values == null) {
            ObjectName pattern = pConfig.findMatchingMBeanPattern(pArg.getName());
            if (pattern != null) {
                values = pConfig.getValues(pArg.getType(),pattern);
            }
        }
        return values != null && (values.contains(pArg.getValue()) || wildcardMatch(values,pArg.getValue()));
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


    // ===========================================================================================

    /**
     * Class encapsulation the arguments for the check command
     */
    public static class Arg {
        private boolean isTypeAllowed;
        private RequestType type;
        private ObjectName name;
        private String value;

        /**
         * Constructor for this immutable object
         *
         * @param pIsTypeAllowed whether the type is allowed in principal (i.e. whether it is mentioned in
         *        s <code>&lt;commands&gt;</code> section)
         * @param pType the type to check
         * @param pName the MBean name to check
         * @param pValue attribute or operation to check
         */
        public Arg(boolean pIsTypeAllowed,RequestType pType, ObjectName pName, String pValue) {
            isTypeAllowed = pIsTypeAllowed;
            type = pType;
            name = pName;
            value = pValue;
        }

        /**
         * Whethe the command type is allowed generally. 
         * @return
         */
        public boolean isTypeAllowed() {
            return isTypeAllowed;
        }

        /**
         * Get request type
         *
         * @return type
         */
        public RequestType getType() {
            return type;
        }

        /**
         * MBean name
         * @return name of MBean
         */
        public ObjectName getName() {
            return name;
        }

        /**
         * Value which is interpreted as operation or attribute name,
         * dependening on the type
         *
         * @return attribute/operation name
         */
        public String getValue() {
            return value;
        }
    }
}
