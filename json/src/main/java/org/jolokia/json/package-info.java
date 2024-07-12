/*
 * Copyright 2009-2024 Roland Huss
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
 * <p>Package with JSON API used throughout Jolokia. This API is a replacement of old {@code com.googlecode.json-simple}
 * API that is designed especially for Jolokia.</p>
 *
 * <p>There are two main entities - <em>JSON Object</em> (a map) and <em>JSON Array</em> (a list) created by parsing
 * JSON using a lexer generated using <a href="https://www.jflex.de/">JFlex</a> - just like json-simple.</p>
 */
package org.jolokia.json;
