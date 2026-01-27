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

/**
 * <p>This package contains Jolokia implementation of the client part of <em>JMX Connector</em></p>.
 *
 * <p>Implementation of {@link javax.management.remote.JMXConnectorFactory} allows Jolokia to be used with tools
 * like JConsole, which connect to JMX Agents using {@link javax.management.remote.JMXServiceURL}.</p>
 *
 * <p>What we're doing here is most importantly a type-safe layer above {@link org.jolokia.client.JolokiaClient} with
 * the type handling controlled by information from {@link javax.management.MBeanInfo}.</p>
 */
package org.jolokia.client.jmxadapter;
