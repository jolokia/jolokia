package org.jolokia.osgi.detector;

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

import java.util.Dictionary;

import org.jolokia.detector.AbstractServerDetector;
import org.jolokia.osgi.servlet.JolokiaServlet;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * @author roland
 * @since 04.12.10
 */
public abstract class AbstractOsgiServerDetector extends AbstractServerDetector {
    protected String getSystemBundleVersion() {
        Dictionary headers = getSystemBundleHeaders();
        return (String) headers.get("Bundle-Version");
    }

    protected String getBundleVersion(String pSymbolicName) {
        BundleContext context = JolokiaServlet.getCurrentBundleContext();
        if (context != null) {
            for (Bundle bundle: context.getBundles()) {
                if (pSymbolicName.equalsIgnoreCase(bundle.getSymbolicName())) {
                    Dictionary headers = bundle.getHeaders();
                    return (String) headers.get("Bundle-Version");
                }
            }
        }
        return null;
    }

    protected boolean checkSystemBundleForSymbolicName(String pSymbolicName) {
        Dictionary headers = getSystemBundleHeaders();
        if (headers != null) {
            String name = (String) headers.get("Bundle-SymbolicName");
            return name.startsWith(pSymbolicName);
        } else {
            // No bundle context given --> no osgi server detection available
            return false;
        }
    }

    private Dictionary getSystemBundleHeaders() {
        BundleContext context = JolokiaServlet.getCurrentBundleContext();
        if (context != null) {
            Bundle systemBundle = context.getBundle(0);
            return systemBundle.getHeaders();
        } else {
            return null;
        }
    }
}
