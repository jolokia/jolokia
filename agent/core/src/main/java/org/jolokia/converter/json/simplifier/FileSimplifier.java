package org.jolokia.converter.json.simplifier;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/*
 * jmx4perl - WAR Agent for exporting JMX via JSON
 *
 * Copyright (C) 2009 Roland Huß, roland@cpan.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * A commercial license is available as well. Please contact roland@cpan.org for
 * further details.
 */

/**
 * Special deserialization for Files to shorten the info
 *
 * @author roland
 * @since Jul 27, 2009
 */
public class FileSimplifier extends SimplifierExtractor<File> {


    public FileSimplifier() {
        super(File.class);
    }

    // ==================================================================================
    @Override
    void init(Map<String, AttributeExtractor<File>> pExtractorMap) {

        Object[][] attrExtractors = {
                { "name", new NameAttributeExtractor() },
                { "modified", new ModifiedAttributeExtractor() },
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
        public Object extract(File file) { return file.getName(); }
    }
    private static class ModifiedAttributeExtractor implements AttributeExtractor<File> {
        public Object extract(File file) { return file.lastModified(); }
    }
    private static class LengthAttributeExtractor implements AttributeExtractor<File> {
        public Object extract(File file) { return file.length(); }
    }
    private static class IsDirectoryAttributeExtractor implements AttributeExtractor<File> {
        public Object extract(File file) { return file.isDirectory(); }
    }
    private static class PathAttributeExtractor implements AttributeExtractor<File> {
        public Object extract(File file) {
            try {
                return file.getCanonicalPath();
            } catch (IOException e) {
                return null;
            }
        }
    }
    private static class ExistsAttributeExtractor implements AttributeExtractor<File> {
        public Object extract(File file) { return file.exists(); }
    }

    private static class LastModifiedAttributeExtractor implements AttributeExtractor<File> {
        public Object extract(File value) { return value.lastModified(); }
    }
}
