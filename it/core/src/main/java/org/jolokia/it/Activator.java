package org.jolokia.it;

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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * 
 * @author roland
 * @since Mar 27, 2010
 */
public class Activator implements BundleActivator {
    private ItSetup itSetup;

    public Activator() {
        itSetup = new ItSetup();
    }

    public void start(BundleContext context) {
        itSetup.start();
    }

    public void stop(BundleContext context) {
        itSetup.stop();
    }
}
