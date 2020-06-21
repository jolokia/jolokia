package org.jolokia.config.address;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/*
 * 
 * Copyright 2020 Georg Tsakumagos
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
 * Service to obtain an {@link InetAddress} to bind the <em>Jolokia</em> service
 * to.
 * 
 * @author Georg Tsakumagos
 * @since 21.06.2020
 *
 */
public interface AddressConfigService {

    /**
     * Optain an address regarding multiple configuration items.
     * 
     * @param agentConfig map holding the configuration in string representation.
     * @return An reference to an address. The reference is <code>null</code> if no
     *         address could be optained from configuration. Then the callee should
     *         bind to all available ip addresses. It is alowed to return an empty
     *         reference to signal the same. The Reference is used to substitute an
     *         Optional wich isn't available in Java 6.
     * @throws RuntimeException If the configuration is invalid.
     */
    public AtomicReference<InetAddress> optain(Map<String, String> agentConfig) throws RuntimeException;

}
