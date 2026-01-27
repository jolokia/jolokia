/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.client.jmxadapter.beans;

/**
 * Two directions of bean - {@link javax.management.openmbean.CompositeData} conversion:<ul>
 *     <li>to {@code CompositeData}: Either implement {@link javax.management.openmbean.CompositeDataView} or
 *     reflectively by calling getters</li>
 *     <li>from {@code CompositeData}:<ul>
 *         <li>{@code static T from(CompositeData cd)} method</li>
 *         <li>record - by calling constructor</li>
 *         <li>class with {@link javax.management.ConstructorParameters} annotation</li>
 *         <li>no-arg constructors and calling setters</li>
 *         <li>for an interface - a Proxy with {@link javax.management.openmbean.CompositeDataInvocationHandler}</li>
 *     </ul></li>
 * </ul>
 */
public class CustomData {

    private final String name;
    private final String[] aliases;

    public CustomData(String name, String[] aliases) {
        this.name = name;
        this.aliases = aliases;
    }

    public String getName() {
        return name;
    }

    public String[] getAliases() {
        return aliases;
    }

}
