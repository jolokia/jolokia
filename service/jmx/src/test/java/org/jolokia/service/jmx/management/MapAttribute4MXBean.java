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
package org.jolokia.service.jmx.management;

import java.net.InetAddress;
import java.util.Map;

public interface MapAttribute4MXBean {

    // https://github.com/jolokia/jolokia/issues/732
    // https://javadoc.io/doc/org.apache.cassandra/cassandra-all/1.2.5/org/apache/cassandra/service/StorageService.html#getOwnership()
    // public java.util.Map<java.net.InetAddress,java.lang.Float> getOwnership()
    Map<InetAddress, Float> getMap();

}
