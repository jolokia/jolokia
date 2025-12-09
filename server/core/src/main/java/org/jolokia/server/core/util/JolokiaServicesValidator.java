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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jolokia.core.api.LogHandler;
import org.jolokia.server.core.service.api.JolokiaService;
import org.jolokia.server.core.service.impl.QuietLogHandler;

public class JolokiaServicesValidator {

    private static boolean warningGiven = false;

    private JolokiaServicesValidator() {}

    public static <T> boolean validateServices(Collection<T> services, LogHandler logHandler) {
        if (logHandler == null) {
            logHandler = new QuietLogHandler();
        }

        // Let's issue a warning if JolokiaService class is also available from system classloader
        if (ClassLoader.getSystemClassLoader() != null) {
            try {
                Class<?> c = ClassLoader.getSystemClassLoader().loadClass(JolokiaService.class.getName());
                if (c != JolokiaService.class && !warningGiven) {
                    logHandler.error("org.jolokia.server.core.service.api.JolokiaService interface is available from multiple class loaders:", null);
                    ClassLoader cl1 = JolokiaService.class.getClassLoader();
                    ClassLoader cl2 = c.getClassLoader();
                    logHandler.error(" - " + (cl1 == null ? "Bootstrap ClassLoader" : cl1.toString()), null);
                    logHandler.error(" - " + (cl2 == null ? "Bootstrap ClassLoader" : cl2.toString()), null);
                    logHandler.error("Possible reason: Multiple Jolokia agents are installed while only a single agent per runtime is supported.", null);
                    logHandler.error("Possible effect: Jolokia service discovery may not work correctly.", null);
                    if (!(logHandler instanceof QuietLogHandler)) {
                        warningGiven = true;
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }
        }

        // however let's issue an error and not return any services if the services use wrong
        // JolokiaService interface
        Set<Class<?>> jolokiaInterfaces = new LinkedHashSet<>();
        // this interface should be loaded by our org.jolokia.core.util.LocalServiceFactory's classloader
        jolokiaInterfaces.add(JolokiaService.class);

        Class<?> theJolokiaServiceClass = jolokiaInterfaces.iterator().next();
        for (Object service : services) {
            if (collectJolokiaServiceInterfaces(service, jolokiaInterfaces, theJolokiaServiceClass) > 0) {
                logHandler.error("Service " + service.getClass().getName() + " loaded from " + service.getClass().getClassLoader() + " uses incompatible JolokiaService interface", null);
            }
        }

        return jolokiaInterfaces.size() == 1;
    }

    private static int collectJolokiaServiceInterfaces(Object service, Set<Class<?>> jolokiaInterfaces, Class<?> expected) {
        Class<?> c = service == null ? null : service.getClass() == Class.class ? (Class<?>) service : service.getClass();
        int count = 0;
        while (c != null && c != Object.class) {
            for (Class<?> iface : c.getInterfaces()) {
                if (iface.getName().equals(JolokiaService.class.getName())) {
                    if (jolokiaInterfaces.add(iface) || iface != expected) {
                        count++;
                    }
                }
                if (collectJolokiaServiceInterfaces(iface, jolokiaInterfaces, expected) > 0) {
                    count++;
                }
            }

            c = c.getSuperclass();
        }

        return count;
    }

}
