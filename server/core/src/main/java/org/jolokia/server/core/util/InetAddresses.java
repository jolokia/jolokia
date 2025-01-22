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
package org.jolokia.server.core.util;

import java.net.Inet4Address;
import java.net.Inet6Address;

/**
 * Pair of IP4/IP6 addresses (may be null) associated with single available network interface
 */
public class InetAddresses {

    private final Inet4Address ia4;
    private final Inet6Address ia6;

    public InetAddresses(Inet4Address ia4, Inet6Address ia6) {
        this.ia4 = ia4;
        this.ia6 = ia6;
    }

    public Inet4Address getIa4() {
        return ia4;
    }

    public Inet6Address getIa6() {
        return ia6;
    }

}
