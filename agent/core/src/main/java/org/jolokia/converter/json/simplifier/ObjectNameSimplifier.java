package org.jolokia.converter.json.simplifier;

import java.util.Map;

import javax.management.ObjectName;

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
 * Special deserialization for ObjectNames to their canonical format
 *
 * @author roland
 * @since Jul 27, 2009
 */
public class ObjectNameSimplifier extends SimplifierExtractor<ObjectName> {

    public ObjectNameSimplifier() {
        super(ObjectName.class);
    }

    // ==================================================================================
    @Override
    void init(Map<String, AttributeExtractor<ObjectName>> pExtractorMap) {
        addExtractors(new Object[][] {{ "objectName", new ObjectNameAttributeExtractor() }});
    }

    private static class ObjectNameAttributeExtractor implements AttributeExtractor<ObjectName> {
        public Object extract(ObjectName value) throws SkipAttributeException {
            return value.getCanonicalName();
        }
    }
}