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

/**
 * The most important responsibility of this package is to provide extraction and serialization into JSON of
 * any objects that come from JMX operation results and attribute retrievals, but because there are two parts of
 * this process:<ul>
 *     <li>extraction</li>
 *     <li>serialization into JSON</li>
 * </ul>
 * This package combines these two elemental operations. Despite the name...
 */
package org.jolokia.converter.json;
