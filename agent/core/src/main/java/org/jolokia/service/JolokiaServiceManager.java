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

import org.jolokia.config.Configuration;
import org.jolokia.util.LogHandler;

/**
 * The service manager is responsible for building up services.
 *
 * @author roland
 * @since 22.04.13
 */
public interface JolokiaServiceManager  {

    public void addService(JolokiaService pService);
    public void removeService(JolokiaService pService);

    public Configuration getConfiguration();
    public LogHandler getLogHandler();

    void addServiceFactory(JolokiaServiceFactory pServiceFactory);

    JolokiaContext start();
    void stop();
}
