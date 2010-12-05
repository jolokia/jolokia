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

import java.util.Dictionary;

import org.jolokia.detector.ServerDetector;
import org.jolokia.osgi.JolokiaActivator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * @author roland
 * @since 04.12.10
 */
abstract public class AbstractOsgiServerDetector implements ServerDetector {
    protected String getSystemBundleVersion() {
        Dictionary headers = getSystemBundleHeaders();
        return (String) headers.get("Bundle-Version");
    }

    protected boolean checkSystemBundleForSymbolicName(String pSymbolicName) {
        Dictionary headers = getSystemBundleHeaders();
        String name = (String) headers.get("Bundle-SymbolicName");
        return name.startsWith(pSymbolicName);
    }

    private Dictionary getSystemBundleHeaders() {
        BundleContext context = JolokiaActivator.getCurrentBundleContext();

        Bundle systemBundle = context.getBundle(0);
        return systemBundle.getHeaders();
    }
}
