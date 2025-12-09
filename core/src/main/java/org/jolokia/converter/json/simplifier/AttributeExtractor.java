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
package org.jolokia.converter.json.simplifier;

/**
 * Helper interface for extracting and simplifying single attributes from objects supported by
 * specific {@link SimplifierAccessor}.
 *
 * @param <T> type to extract
 */
public interface AttributeExtractor<T> {

    /**
     * Extract the real value from a given value
     *
     * @param value to extract from
     * @return the extracted value
     * @throws SkipAttributeException if this value which is about to be extracted should be omitted in the result
     */
    Object extract(T value) throws SkipAttributeException;

    /**
     * Exception to be thrown when the result of this extractor should be omitted in the response
     */
    class SkipAttributeException extends Exception {
    }

}
