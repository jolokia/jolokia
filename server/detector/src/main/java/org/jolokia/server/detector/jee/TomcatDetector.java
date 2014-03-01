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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jolokia.server.core.detector.DefaultServerHandle;
import org.jolokia.server.core.detector.ServerHandle;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;

/**
 * Detector for Tomcat
 *
 * @author roland
 * @since 05.11.10
 */
public class TomcatDetector extends AbstractServerDetector {

    private static final Pattern SERVER_INFO_PATTERN =
            Pattern.compile("^\\s*([^/]+)\\s*/\\s*([\\d\\.]+(-RC\\d+)?)",Pattern.CASE_INSENSITIVE);


    /**
     * Create a server detector
     *
     * @param pOrder of the detector (within the list of detectors)
     */
    public TomcatDetector(int pOrder) {
        super("tomcat",pOrder);
    }


    /** {@inheritDoc}
     * @param pMBeanServerAccess*/
    public ServerHandle detect(MBeanServerAccess pMBeanServerAccess) {
        String serverInfo = getSingleStringAttribute(pMBeanServerAccess, "*:type=Server", "serverInfo");
        if (serverInfo == null) {
            return null;
        }
        Matcher matcher = SERVER_INFO_PATTERN.matcher(serverInfo);
        if (matcher.matches()) {
            String product = matcher.group(1);
            String version = matcher.group(2);
            // TODO: Extract access URL
            if (product.toLowerCase().contains("tomcat")) {
                return new DefaultServerHandle("Apache",getName(),version);
            }
        }
        return null;
    }

}
