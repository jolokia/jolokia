package org.jolokia.jmx;

/*
 * Copyright 2009-2013 Roland Huss
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

import javax.management.MBeanServer;

/**
 * A wrapper class for holding the Jolokia JSR-160 private MBeanServer
 *
 * @author roland
 * @since 14.01.13
 */
class JolokiaMBeanServerHolder implements JolokiaMBeanServerHolderMBean {

    private MBeanServer jolokiaMBeanServer;

    /**
     * Create a new holder
     */
    public JolokiaMBeanServerHolder() {
        jolokiaMBeanServer = new JolokiaMBeanServer();
    }

    /**
     * Get the managed JolokiaMBeanServer
     *
     * @return the Jolokia MBean Server
     */
    public MBeanServer getJolokiaMBeanServer() {
        return jolokiaMBeanServer;
    }
}
