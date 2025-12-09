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
package org.jolokia.converter.object;

/**
 * Generic deserialization/conversion interface.
 * @param <T>
 */
public interface Converter<T> {

    /**
     * <p>Generic deserialization/conversion method, where:<ul>
     *     <li>{@code targetType} describes the desired type of the deserialized object (deserializers usually use {@link Class},
     *     but we have other ways to specify the target type).</li>
     *     <li>{@code value} comes in some wire format (String or JSON), but we can also
     *     <em>convert</em> (which is wider term than <em>deserialization</em>) them from other types.</li>
     * </ul>
     * </p>
     *
     * @param targetType
     * @param value
     * @return
     */
    Object convert(T targetType, Object value);

}
