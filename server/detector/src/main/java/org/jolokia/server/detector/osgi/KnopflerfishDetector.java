package org.jolokia.server.detector.osgi;

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

import org.jolokia.server.core.detector.DefaultServerHandle;
import org.jolokia.server.core.service.api.ServerHandle;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.osgi.framework.BundleContext;

/**
 * Detector for the Apache Felix OSGi Platform
 *
 * @author roland
 * @since 02.12.10
 */
public class KnopflerfishDetector extends AbstractOsgiServerDetector {

    /**
     * Create a server detector
     *
     * @param pContext OSGi context
     */
    public KnopflerfishDetector(BundleContext pContext) {
        super(pContext,"knopflerfish");
    }

    /** {@inheritDoc}
     * @param pMBeanServerAccess*/
    public ServerHandle detect(MBeanServerAccess pMBeanServerAccess) {
        // Probably a bit unspecific, but that's kopflerfish's fault
        if (checkSystemBundleForSymbolicName("org.knopflerfish.framework")) {
            String version = getSystemBundleVersion();
            return new DefaultServerHandle("Knopflerfish",getName(),version);
        } else {
            return null;
        }
    }
}
