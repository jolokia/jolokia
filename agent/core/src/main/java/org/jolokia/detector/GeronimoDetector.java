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

package org.jolokia.detector;

import org.jolokia.backend.MBeanServerManager;

/**
 * Detector for the Geronimo JEE Server
 * 
 * @author roland
 * @since 05.12.10
 */
public class GeronimoDetector extends AbstractServerDetector {

    /** {@inheritDoc}
     * @param pMBeanServerManager*/
    public ServerHandle detect(MBeanServerManager pMBeanServerManager) {
        String version = getSingleStringAttribute(pMBeanServerManager,"geronimo:j2eeType=J2EEServer,*","serverVersion");
        if (version != null) {
            return new ServerHandle("Apache","geronimo",version,null,null);
        } else {
            return null;
        }
    }
}
