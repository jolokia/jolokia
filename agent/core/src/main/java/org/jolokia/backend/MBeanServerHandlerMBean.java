package org.jolokia.backend;

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

/**
 * MBean interface for accessing the {@link MBeanServerHandler}
 *
 * @author roland
 * @since Jul 2, 2010
 */
public interface MBeanServerHandlerMBean {

    /**
     * Name of MBean used for registration
     */
    String OBJECT_NAME = "jolokia:type=ServerHandler";

    /**
     * Get a summary information of all MBeans found on the server
     *
     * @return the servers information.
     */
    String mBeanServersInfo();
}
