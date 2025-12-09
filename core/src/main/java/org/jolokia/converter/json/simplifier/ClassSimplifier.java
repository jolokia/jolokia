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
package org.jolokia.converter.json.simplifier;

/**
 * Simplifier for class objects. It adds a <code>name</code> value and a list of
 * implementing interfaces under the <code>interfaces</code> key.
 *
 * @author roland
 * @since Jul 27, 2009
 */
@SuppressWarnings("rawtypes")
public class ClassSimplifier extends SimplifierAccessor<Class> {

    /**
     * Empty constructor
     */
    public ClassSimplifier() {
        super(Class.class);

        addExtractor("name", new NameAttributeExtractor());
        addExtractor("interfaces", new InterfaceAttributeExtractor());
    }

    @Override
    public String extractString(Object pValue) {
        return ((Class<?>) pValue).getName();
    }

    private static class NameAttributeExtractor implements AttributeExtractor<Class> {
        @Override
        public Object extract(Class pClass) {
            return pClass.getName();
        }
    }

    private static class InterfaceAttributeExtractor implements AttributeExtractor<Class> {
        @Override
        public Object extract(Class value) throws SkipAttributeException {
            if (value.isInterface()) {
                throw new SkipAttributeException();
            }
            return value.getInterfaces();
        }
    }

}
