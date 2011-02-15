/*
 * Copyright 2009-2011 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.osgi.detector;

import java.util.*;

import javax.management.MBeanServer;

import org.jolokia.detector.ServerHandle;

/**
 * Detector for Eclipse Virgo
 *
 * @author roland
 * @since 02.12.10
 */
public class VirgoDetector extends AbstractOsgiServerDetector {

    public ServerHandle detect(Set<MBeanServer> pMbeanServers) {
        String version = getBundleVersion("org.eclipse.virgo.kernel.agent.dm");
        if (version != null) {
            String type = getBundleVersion("org.eclipse.gemini.web.core") != null ? "web" : "kernel";
            Map<String,String> extraInfo = new HashMap<String,String>();
            extraInfo.put("type",type);
            return new ServerHandle("Eclipse","Virgo",version,null,extraInfo);
        } else {
            return null;
        }
    }
}
