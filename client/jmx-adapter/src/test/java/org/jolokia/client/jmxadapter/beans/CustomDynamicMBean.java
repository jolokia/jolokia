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

import javax.management.MBeanInfo;
import javax.management.openmbean.TabularData;

/**
 * <p>If this interface was an {@link javax.management.MXBean}, {@code CustomTabularData} attribute would be mapped
 * to ... {@link javax.management.openmbean.CompositeType}, because it'd be treated as <em>other</em> type.</p>
 *
 * <p>If we just register an implementation of this interface it'd become a {@link javax.management.StandardMBean}
 * where {@code CustomTabularData} attribute would just be of {@link TabularData} type without associated
 * {@link javax.management.openmbean.TabularType}. So the only way to actually provide a {@link javax.management.openmbean.TabularType}
 * would be to register a {@link javax.management.DynamicMBean} where {@link MBeanInfo#getAttributes()} returns
 * a customized attribute using {@link javax.management.openmbean.OpenMBeanAttributeInfo}.</p>
 */
public interface CustomDynamicMBean {

    TabularData getCustomTabularData();

}
