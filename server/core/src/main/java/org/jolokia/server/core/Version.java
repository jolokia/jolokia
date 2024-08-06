package org.jolokia.server.core;

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


import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Class holding the version of this agent and the protocol.
 *
 * @author roland
 * @since Jun 11, 2009
 */
public final class Version {

    private static final String VERSION;

    // Major.Minor version of protocol
    private static final String PROTOCOL;

    static {
        String version;
        String protocol;
        try (InputStream is = Version.class.getResourceAsStream("/version.properties")) {
            Properties props = new Properties();
            props.load(is);
            version = props.getProperty("jolokia.version");
            protocol = props.getProperty("protocol.version");
        } catch (IOException e) {
            // should never happen, unless this code is repackaged in different project
            version = "2.1.0";
            protocol = "8.0";
        }
        VERSION = version;
        PROTOCOL = protocol;
    }

    private Version() {}

    public static String getAgentVersion() {
        return VERSION;
    }

    public static String getProtocolVersion() {
        return PROTOCOL;
    }
}
