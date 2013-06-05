package org.jolokia.jvmagent.spring;

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

import org.jolokia.jmx.JolokiaMBeanServerUtil;
import org.springframework.beans.factory.FactoryBean;

/**
 * Simple Factory bean for looking up the jolokia MBeanServer
 *
 * @author roland
 * @since 11.02.13
 */
public class SpringJolokiaMBeanServerFactory implements FactoryBean<MBeanServer> {

    /**
     * Get the Jolokia MBeanServer. This call is delegated to the corresponding
     * static utility method and will register a new MBeanServer if not already
     * present
     *
     * @return the Jolokia MBeanServer
     */
    public MBeanServer getObject() {
        return JolokiaMBeanServerUtil.getJolokiaMBeanServer();
    }

    /** {@inheritDoc} */
    public Class<?> getObjectType() {
        return MBeanServer.class;
    }

    /** {@inheritDoc} */
    public boolean isSingleton() {
        return true;
    }
}
