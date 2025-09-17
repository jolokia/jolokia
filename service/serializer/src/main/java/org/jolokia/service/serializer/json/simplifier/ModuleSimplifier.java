/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.service.serializer.json.simplifier;

import java.util.Set;

import org.jolokia.json.JSONArray;

public class ModuleSimplifier extends SimplifierAccessor<Module> {

    public ModuleSimplifier() {
        super(Module.class);

        addExtractor("module", new NameAttributeExtractor());
        addExtractor("packages", new PackagesAttributeExtractor());
    }

    @Override
    public String extractString(Object pValue) {
        return ((Module) pValue).getName();
    }

    private static class NameAttributeExtractor implements AttributeExtractor<Module> {
        @Override
        public Object extract(Module mod) {
            return mod.getName();
        }
    }

    private static class PackagesAttributeExtractor implements AttributeExtractor<Module> {
        @Override
        public Object extract(Module mod) {
            Set<String> packages = mod.getPackages();
            if (packages == null || packages.isEmpty()) {
                return null;
            }
            return new JSONArray(packages);
        }
    }

}
