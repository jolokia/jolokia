/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.server.core.config;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Map;
import java.util.Properties;

import org.jolokia.server.core.util.InetAddresses;
import org.jolokia.server.core.util.NetworkUtil;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.AssertJUnit.assertNull;

public class StaticConfigurationTest {

    @Test
    public void emptyConfig() {
        StaticConfiguration config = new StaticConfiguration();
        assertEquals(config.getConfigKeys().size(), 0);

        assertNull(config.getConfig(ConfigKey.AGENT_ID));
    }

    @Test
    public void plainConfig() {
        StaticConfiguration config = new StaticConfiguration(ConfigKey.AGENT_TYPE, "orange");
        assertEquals(config.getConfigKeys().size(), 1);

        assertEquals(config.getConfig(ConfigKey.AGENT_TYPE), "orange");
    }

    @Test
    public void defaultConfig() {
        StaticConfiguration config = new StaticConfiguration();
        assertEquals(config.getConfigKeys().size(), 0, "defaults do not count as known property keys");

        assertEquals(config.getConfig(ConfigKey.AGENT_CONTEXT), "/jolokia");
    }

    @Test
    public void overridenDefaultConfig() {
        StaticConfiguration config = new StaticConfiguration(ConfigKey.AGENT_CONTEXT, "/agent");
        assertEquals(config.getConfigKeys().size(), 1);

        assertEquals(config.getConfig(ConfigKey.AGENT_CONTEXT), "/agent");
    }

    @Test
    public void sysOverridenDefaultConfig() {
        StaticConfiguration config = new StaticConfiguration() {
            @Override
            protected Properties sys() {
                Properties props = new Properties();
                props.setProperty("jolokia.agentContext", "/agent");
                return props;
            }
        };
        assertEquals(config.getConfigKeys().size(), 1);

        assertEquals(config.getConfig(ConfigKey.AGENT_CONTEXT), "/agent");
    }

    @Test
    public void envOverridenDefaultConfig() {
        StaticConfiguration config = new StaticConfiguration() {
            @Override
            protected Map<String, String> env() {
                return Map.of("JOLOKIA_AGENT_CONTEXT", "/agent");
            }
        };
        assertEquals(config.getConfigKeys().size(), 1);

        assertEquals(config.getConfig(ConfigKey.AGENT_CONTEXT), "/agent");
    }

    @Test
    public void envAndSysBasedConfig() {
        StaticConfiguration config = new StaticConfiguration() {
            @Override
            protected Map<String, String> env() {
                return Map.of(
                    "JOLOKIA_DEBUG", "true",
                    "JOLOKIA_LOG_HANDLER_CLASS", "java.lang.Object"
                );
            }

            @Override
            protected Properties sys() {
                Properties props = new Properties();
                props.setProperty("jolokia.debug", "false");
                props.setProperty("jolokia.user", "Howard");
                return props;
            }
        };

        assertEquals(config.getConfigKeys().size(), 3);
        assertEquals(config.getConfig(ConfigKey.DEBUG), "false", "sys overrides env");
        assertEquals(config.getConfig(ConfigKey.LOGHANDLER_CLASS), "java.lang.Object");
        assertEquals(config.getConfig(ConfigKey.USER), "Howard");
    }

    @Test
    public void sysEnvPlaceholders() {
        StaticConfiguration config = new StaticConfiguration() {
            @Override
            protected Map<String, String> env() {
                return Map.of(
                    "LOG_HANDLER_CLASS", "lang.Object",
                    ConfigKey.AUTH_IGNORE_CERTS.asEnvVariable(), "${prop:debug}"
                );
            }

            @Override
            protected Properties sys() {
                Properties props = new Properties();
                props.setProperty("debug", "true");
                props.setProperty("jolokia.debug", "${sys:debug}");
                props.setProperty("jolokia.logHandlerClass", "java.${env:LOG_HANDLER_CLASS}");
                props.setProperty(ConfigKey.DETECTOR_OPTIONS.asSystemProperty(), "${sys:jolokia.debug}");
                return props;
            }
        };

        assertEquals(config.getConfig(ConfigKey.DEBUG), "true");
        assertEquals(config.getConfig(ConfigKey.LOGHANDLER_CLASS), "java.lang.Object");
        assertEquals(config.getConfig(ConfigKey.AUTH_IGNORE_CERTS), "true");
        assertEquals(config.getConfig(ConfigKey.DETECTOR_OPTIONS), "${sys:debug}", "only one resolution");
    }

    @Test
    public void placeholders() {
        StaticConfiguration config = new StaticConfiguration() {
            @Override
            protected Map<String, String> env() {
                return Map.of(
                    "LOG_HANDLER_CLASS", "lang.Object"
                );
            }

            @Override
            protected Properties sys() {
                Properties props = new Properties();
                props.setProperty("debug", "true");
                props.setProperty("debug2", "${sys:debug}");
                return props;
            }
        };
        Properties props = new Properties();
        props.setProperty(ConfigKey.DEBUG.getKeyValue(), "${sys:debug}");
        props.setProperty(ConfigKey.LOGHANDLER_CLASS.getKeyValue(), "java.${env:LOG_HANDLER_CLASS}");
        props.setProperty(ConfigKey.DETECTOR_OPTIONS.getKeyValue(), "${sys:debug2}");
        props.setProperty(ConfigKey.AUTH_IGNORE_CERTS.getKeyValue(), "${prop:debug}");
        config.update(new PropertiesConfigExtractor(props));

        assertEquals(config.getConfig(ConfigKey.DEBUG), "true");
        assertEquals(config.getConfig(ConfigKey.LOGHANDLER_CLASS), "java.lang.Object");
        assertEquals(config.getConfig(ConfigKey.AUTH_IGNORE_CERTS), "true");
        assertEquals(config.getConfig(ConfigKey.DETECTOR_OPTIONS), "${sys:debug}", "only one resolution");
    }

    @Test
    public void hostsAndIPs() {
        StaticConfiguration config = new StaticConfiguration();
        Properties props = new Properties();
        props.setProperty(ConfigKey.AGENT_ID.getKeyValue(), "jolokia-${ip}/${host}");
        String world = System.getProperty("os.name").startsWith("Mac") ? "world-of-${ip6:lo0}" : "world-of-${ip6:lo}";
        props.setProperty(ConfigKey.REALM.getKeyValue(), world);
        config.update(new PropertiesConfigExtractor(props));

        NetworkInterface nif = NetworkUtil.getBestMatchNetworkInterface();
        assertNotNull(nif);
        Map<String, InetAddresses> map = NetworkUtil.getBestMatchAddresses();
        assertEquals(config.getConfig(ConfigKey.AGENT_ID), "jolokia-"
            + map.get(nif.getName()).getIa4().map(Inet4Address::getHostAddress).orElse(null)
            + "/"
            + map.get(nif.getName()).getIa4().map(Inet4Address::getHostName).orElse(null));
        if (NetworkUtil.isIPv6Supported()) {
            assertEquals(config.getConfig(ConfigKey.REALM), "world-of-0:0:0:0:0:0:0:1");
        }
    }

}
