package org.jolokia.server.detector.jee;

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

import java.lang.reflect.*;

import org.jolokia.server.core.detector.DefaultServerHandle;
import org.jolokia.server.core.service.api.ServerHandle;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.server.core.util.ClassUtil;

/**
 * A detector for jetty
 *
 * @author roland
 * @since 05.11.10
 */
public class JettyDetector extends AbstractServerDetector {


    /**
     * Create a server detector
     *
     * @param pOrder of the detector (within the list of detectors)
     */
    public JettyDetector(int pOrder) {
        super("jetty",pOrder);
    }

    /** {@inheritDoc}
     * @param pMBeanServerAccess*/
    public ServerHandle detect(MBeanServerAccess pMBeanServerAccess) {
        Class<?> serverClass = ClassUtil.classForName("org.mortbay.jetty.Server", false);
        if (serverClass != null) {
            return new DefaultServerHandle("Mortbay", getName(), getVersion(serverClass));
        }
        serverClass = ClassUtil.classForName("org.eclipse.jetty.server.Server",false);
        if (serverClass != null) {
            return new DefaultServerHandle("Eclipse", getName(), getVersion(serverClass));
        }
        return null;
    }

    private String getVersion(Class<?> serverClass) {
        try {
            Method method = serverClass.getMethod("getVersion");
            if (Modifier.isStatic(method.getModifiers())) {
                return (String) method.invoke(null);
            } else {
                Constructor<?> ctr = serverClass.getConstructor();
                Object server = ctr.newInstance();
                return (String) method.invoke(server);
            }
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException ignored) {
        }
        return null;
    }
}
