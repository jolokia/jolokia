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
 * Class combining various maps for attributes, operations and name patterns
 *
 * @author roland
 * @since 03.09.11
 */

public class MBeanPolicyConfig {

    private Set<ObjectName>             patterns = new HashSet<ObjectName>();
    private Map<ObjectName,Set<String>> readAttributes = new HashMap<ObjectName, Set<String>>();
    private Map<ObjectName,Set<String>> writeAttributes = new HashMap<ObjectName, Set<String>>();
    private Map<ObjectName,Set<String>> operations = new HashMap<ObjectName, Set<String>>();

    public void addPattern(ObjectName pObjectName) {
        patterns.add(pObjectName);
    }

    public void addValues(ObjectName pOName, Set<String> pReadAttributes, Set<String> pWriteAttributes, Set<String> pOperations) {
        readAttributes.put(pOName,pReadAttributes);
        writeAttributes.put(pOName,pWriteAttributes);
        operations.put(pOName,pOperations);
        if (pOName.isPattern()) {
            addPattern(pOName);
        }
    }

    public Set<String> getValues(RequestType pType, ObjectName pName) {
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

    public ObjectName findMatchingMBeanPattern(ObjectName pName) {
        // Check all stored patterns for a match and return the pattern if one is found
        for (ObjectName pattern : patterns) {
            if (pattern.apply(pName)) {
                return pattern;
            }
        }
        return null;
    }
}
