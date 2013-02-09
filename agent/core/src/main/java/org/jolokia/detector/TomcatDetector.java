package org.jolokia.detector;

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

import org.jolokia.backend.executor.MBeanServerExecutor;

/**
 * Detector for Tomcat
 *
 * @author roland
 * @since 05.11.10
 */
public class TomcatDetector extends AbstractServerDetector {

    private static final Pattern SERVER_INFO_PATTERN = Pattern.compile("^\\s*([^/]+)\\s*/\\s*([\\d\\.]+)");


    /** {@inheritDoc}
     * @param pMBeanServerExecutor*/
    public ServerHandle detect(MBeanServerExecutor pMBeanServerExecutor) {
        String serverInfo = getSingleStringAttribute(pMBeanServerExecutor, "*:type=Server", "serverInfo");
        if (serverInfo == null) {
            return null;
        }
        Matcher matcher = SERVER_INFO_PATTERN.matcher(serverInfo);
        if (matcher.matches()) {
            String product = matcher.group(1);
            String version = matcher.group(2);
            // TODO: Extract access URL
            if (product.toLowerCase().contains("tomcat")) {
                return new ServerHandle("Apache","tomcat",version,null,null);
            }
        }
        return null;
    }

}
