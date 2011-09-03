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

import org.w3c.dom.Node;

/**
 * Base class for all checkers
 *
 * @param <T> type used for checking the access
 *
 * @author roland
 * @since 03.09.11
 */
public abstract class AbstractChecker<T> {

    /**
     * Verify that a given node has one of a set of expected node names
     *
     * @param pNode node to check
     * @param pExpected list of expected node names
     * @throws SecurityException if the node has none of the expected names
     */
    protected void assertNodeName(Node pNode, String ... pExpected)  {
        for (String expected : pExpected) {
            if (pNode.getNodeName().equals(expected)) {
                return;
            }
        }
        StringBuilder buffer = new StringBuilder();
        for (int i=0; i < pExpected.length; i++) {
            buffer.append(pExpected[i]);
            if (i < pExpected.length-1) {
                buffer.append(",");
            }
        }
        throw new SecurityException(
                "Expected element " + buffer.toString() + " but got " + pNode.getNodeName());
    }

    /**
     * Check whether for the given argument access is allowed
     *
     * @param pArg argument (specific to each subclass)
     * @return true if access is allowed, false otherwise
     */
    public abstract boolean check(T pArg);
}
