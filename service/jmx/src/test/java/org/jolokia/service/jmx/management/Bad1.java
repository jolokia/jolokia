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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

public class Bad1 implements Bad1MBean {

    @Override
    public Reader getReader() {
        return new InputStreamReader(new ByteArrayInputStream(new byte[0]));
    }

    @Override
    public Map<Object, Object> getUglyMap() {
        try {
            return Map.of(
                new File("/tmp/a1.txt"), InetAddress.getByAddress("localhost", new byte[] { 127, 0, 0, 1 }),
                InetAddress.getByAddress("everfree.forest", new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 }), new File("/tmp/a2.txt")
            );
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

}
