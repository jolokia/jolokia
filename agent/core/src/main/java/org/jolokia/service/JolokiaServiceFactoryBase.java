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

package org.jolokia.service;

import org.jolokia.config.ConfigKey;
import org.jolokia.util.LogHandler;

/**
 * @author roland
 * @since 25.04.13
 */
abstract public class JolokiaServiceFactoryBase implements JolokiaServiceFactory {

    JolokiaServiceManager serviceManager;

    public void init(JolokiaServiceManager pServiceManager) {
        serviceManager = pServiceManager;
        doInit();
    }

    abstract protected void doInit();

    protected LogHandler getLogHandler() {
        return serviceManager.getLogHandler();
    }

    protected String getConfig(ConfigKey pConfigKey) {
        return serviceManager.getConfiguration().getConfig(pConfigKey);
    }


    protected void addService(JolokiaService pService) {
        serviceManager.addService(pService);
    }


    protected void info(String pMessage) {
        getLogHandler().info(pMessage);
    }


    public void destroy() {
        serviceManager = null;
    }


}
