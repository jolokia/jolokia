/*
 * Copyright 2009-2010 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.osgi.detector;

import org.jolokia.detector.AbstractServerDetector;
import org.jolokia.detector.ServerInfo;
import org.jolokia.osgi.JolokiaActivator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;

import javax.management.MBeanServer;
import java.util.Dictionary;
import java.util.Set;

/**
 * Detector for the Apache Felix OSGi Platform
 *
 * @author roland
 * @since 02.12.10
 */
public class FelixDetector extends AbstractServerDetector {
    public ServerInfo detect(Set<MBeanServer> pMbeanServers) {
        BundleContext context = JolokiaActivator.getCurrentBundleContext();

        Bundle systemBundle = context.getBundle(0);
        Dictionary headers = systemBundle.getHeaders();
        String name = (String) headers.get("Bundle-SymbolicName");
        if ("org.apache.felix.framework".equals(name)) {
            String version = (String) headers.get("Bundle-Version");
            return new ServerInfo("Apache","felix",version,null,null);
        } else {
            return null;
        }
    }
}
