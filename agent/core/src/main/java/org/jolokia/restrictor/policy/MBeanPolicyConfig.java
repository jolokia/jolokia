package org.jolokia.restrictor.policy;

import java.util.*;

import javax.management.ObjectName;

import org.jolokia.util.RequestType;

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

/**
 * Class combining various maps for attributes, operations and name patterns. It is used
 * internally by {@libk MBeanAccessChecker} to store the policy configuration.
 *
 * @author roland
 * @since 03.09.11
 */

class MBeanPolicyConfig {

    private Set<ObjectName>             patterns = new HashSet<ObjectName>();
    private Map<ObjectName,Set<String>> readAttributes = new HashMap<ObjectName, Set<String>>();
    private Map<ObjectName,Set<String>> writeAttributes = new HashMap<ObjectName, Set<String>>();
    private Map<ObjectName,Set<String>> operations = new HashMap<ObjectName, Set<String>>();

    /**
     * Add a object name pattern
     *
     * @param pObjectName pattern to add
     */
    void addPattern(ObjectName pObjectName) {
        patterns.add(pObjectName);
    }

    /**
     * Add for a given MBean a set of read/write attributes and operations
     *
     * @param pOName MBean name (which should not be pattern)
     * @param pReadAttributes read attributes
     * @param pWriteAttributes write attributes
     * @param pOperations operations
     */
    void addValues(ObjectName pOName, Set<String> pReadAttributes, Set<String> pWriteAttributes, Set<String> pOperations) {
        readAttributes.put(pOName,pReadAttributes);
        writeAttributes.put(pOName,pWriteAttributes);
        operations.put(pOName,pOperations);
        if (pOName.isPattern()) {
            addPattern(pOName);
        }
    }

    /**
     * Get the set of stored values for a given MBean and type (read/write/exec)
     *
     * @param pType request type for which the previously added values should be retrieved.
     * @param pName MBean
     * @return set of previously added value or <code>null</code> if none has been added for this MBean/type.
     */
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

    /**
     * Given a MBean name return a pattern previously added and which matches this MBean name. Note,
     * that patterns might not overlap since the order in which they are tried is undefined.
     *
     * @param pName name to match against
     * @return the pattern found or <code>null</code> if none has been found.
     */
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
