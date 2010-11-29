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

import org.json.simple.JSONObject;

import javax.management.MBeanServer;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detector for IBM Websphere 6 & 7
 *
 * @author roland
 * @since 29.11.10
 */
public class WebsphereDetector extends AbstractServerDetector {

    private final static Pattern SERVER_VERSION_PATTERN =
            Pattern.compile("^Version\\s+([0-9.]+)\\s*$.*?^Build Date\\s+([0-9/]+)\\s*$",
                    Pattern.MULTILINE | Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public ServerInfo detect(Set<MBeanServer> pMbeanServers) {
        String platformName =
                getSingleStringAttribute(pMbeanServers, "*:j2eeType=J2EEServer,type=Server,*", "platformName");
        if (platformName != null && platformName.contains("WebSphere")) {
            String serverVersion =
                    getSingleStringAttribute(pMbeanServers, "*:j2eeType=J2EEServer,type=Server,*", "serverVersion");
            if (serverVersion != null) {
                Matcher matcher = SERVER_VERSION_PATTERN.matcher(serverVersion);
                if (matcher.find()) {
                    String version = matcher.group(1);
                    String date = matcher.group(2);
                    JSONObject extraInfo = new JSONObject();
                    if (date != null) {
                        extraInfo.put("buildDate",date);
                    }
                    // TODO: Extract access URL
                    return new ServerInfo("IBM","websphere",version,null,extraInfo.size() > 0 ? extraInfo : null);
                }
            }
            return null;
        }
        return null;
    }
}
