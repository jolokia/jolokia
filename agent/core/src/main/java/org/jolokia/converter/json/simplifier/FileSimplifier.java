package org.jolokia.converter.json.simplifier;

import java.io.File;
import java.io.IOException;

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
 * Special deserialization for Files to shorten the info
 *
 * @author roland
 * @since Jul 27, 2009
 */
public class FileSimplifier extends SimplifierExtractor<File> {

    /**
     * Default constructor
     */
    public FileSimplifier() {
        super(File.class);

        Object[][] attrExtractors = {
                { "name", new NameAttributeExtractor() },
                { "length", new LengthAttributeExtractor() },
                { "directory", new IsDirectoryAttributeExtractor() },
                { "canonicalPath", new PathAttributeExtractor() },
                { "exists", new ExistsAttributeExtractor() },
                { "lastModified", new LastModifiedAttributeExtractor()}
        };

        addExtractors(attrExtractors);
    }

    // ==========================================================================
    // Static inner classes as usage extractors
    private static class NameAttributeExtractor implements AttributeExtractor<File> {
        /** {@inheritDoc} */
        public Object extract(File file) { return file.getName(); }
    }

    private static class LengthAttributeExtractor implements AttributeExtractor<File> {
        /** {@inheritDoc} */
        public Object extract(File file) { return file.length(); }
    }

    private static class IsDirectoryAttributeExtractor implements AttributeExtractor<File> {
        /** {@inheritDoc} */
        public Object extract(File file) { return file.isDirectory(); }
    }

    private static class PathAttributeExtractor implements AttributeExtractor<File> {
        /** {@inheritDoc} */
        public Object extract(File file) {
            try {
                return file.getCanonicalPath();
            } catch (IOException e) {
                return null;
            }
        }
    }
    private static class ExistsAttributeExtractor implements AttributeExtractor<File> {
        /** {@inheritDoc} */
        public Object extract(File file) { return file.exists(); }
    }

    private static class LastModifiedAttributeExtractor implements AttributeExtractor<File> {
        /** {@inheritDoc} */
        public Object extract(File value) { return value.lastModified(); }
    }
}
