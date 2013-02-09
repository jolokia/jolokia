package org.jolokia.converter.json.simplifier;

import java.util.Map;

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


/**
 * Simplifier for class objects. It adds a <code>name</code> value and a list of
 * implementing interfaces under the <code>interfaces</code> key.
 *
 * @author roland
 * @since Jul 27, 2009
 */
public class ClassSimplifier extends SimplifierExtractor<Class> {

    /**
     * Empty constructor
     */
    public ClassSimplifier() {
        super(Class.class);
    }

    /** {@inheritDoc} */
    @Override
    void init(Map<String, AttributeExtractor<Class>> pStringExtractorMap) {
        Object[][] pAttrs = {
                { "name", new NameAttributeExtractor() },
                { "interfaces", new InterfaceAttributeExtractor() }
        };
        addExtractors(pAttrs);
    }

    // ==================================================================================

    private static class NameAttributeExtractor implements AttributeExtractor<Class> {
        /** {@inheritDoc} */
        public Object extract(Class pClass) { return pClass.getName(); }
    }

    private static class InterfaceAttributeExtractor implements AttributeExtractor<Class> {
        /** {@inheritDoc} */
        public Object extract(Class value) throws AttributeExtractor.SkipAttributeException {
            if (value.isInterface()) {
                throw new SkipAttributeException();
            }
            return value.getInterfaces();
        }
    }
}
