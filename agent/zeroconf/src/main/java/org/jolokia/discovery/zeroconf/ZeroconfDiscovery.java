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
package org.jolokia.discovery.zeroconf;

import org.jolokia.discovery.AgentDetails;
import org.jolokia.discovery.DiscoveryListener;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An implementation of the {@link DiscoveryListener} which uses ZeroConf to publish
 * jolokia details on the local machine
 */
public class ZeroconfDiscovery implements DiscoveryListener {

    /**
     * The ZeroConf type name to register into ZeroConf
     */
    public static final String JOLOKIA_ZEROCONF_TYPE = "_http._tcp.local.";

    private JmDNS jmDNS;
    private Map<AgentDetails, ServiceInfo> services = new ConcurrentHashMap<AgentDetails, ServiceInfo>();

    public ZeroconfDiscovery() throws IOException {
    }

    public ZeroconfDiscovery(JmDNS jmDNS) {
        this.jmDNS = jmDNS;
    }

    public JmDNS getJmDNS() {
        return jmDNS;
    }

    public void setJmDNS(JmDNS jmDNS) {
        this.jmDNS = jmDNS;
    }

    public void onAgentStarted(AgentDetails details) {
        if (jmDNS == null) {
            // lets lazy create using the current location host
            String defaultHostName = extractHostNameFromUrl(details);
            try {
                jmDNS = createJmDNS(defaultHostName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // there may be a window where we re-create a service for a URL
        // but given there's usually a single service instance per JVM, its not a biggie ;)
        ServiceInfo serviceInfo = services.get(details);
        if (serviceInfo == null) {
            serviceInfo = createServiceInfo(details);
            services.put(details, serviceInfo);
            try {
                jmDNS.registerService(serviceInfo);
            } catch (IOException e) {
                System.out.println("Failed to register " + serviceInfo + " into ZeroConf: " + e);
                e.printStackTrace();
            }
        }
    }

    protected  String extractHostNameFromUrl(AgentDetails details) {
        String location = details.getLocation();
        String answer = null;
        try {
            URL url = new URL(location);
            answer = url.getHost();
        } catch (Exception e) {
            System.out.println("Failed to parse port of location URL: " + location + ". " + e);
            e.printStackTrace();
        }
        if (answer == null) {
            answer = "localhost";
        }
        return answer;
    }

    public void onAgentStopped(AgentDetails details) {
        ServiceInfo serviceInfo = services.remove(details);
        if (serviceInfo != null) {
            try {
                jmDNS.unregisterService(serviceInfo);
            } catch (Exception e) {
                System.out.println("Failed to unregister " + serviceInfo + " into ZeroConf: " + e);
                e.printStackTrace();
            }
        }
    }

    protected ServiceInfo createServiceInfo(AgentDetails details) {
        String location = details.getLocation();
        int port = 0;
        String path = "";
        try {
            URL url = new URL(location);
            port = url.getPort();
            path = url.getPath();
        } catch (Exception e) {
            System.out.println("Failed to parse port of location URL: " + location + ". " + e);
            e.printStackTrace();
        }
        String text = location;
        String name = details.getName() + " [" + port + "]";
        int weight = 1;
        int priority = 1;
        boolean persistent = false;
        Map<String, String> props = new HashMap<String, String>();
        props.put("path", path);
        props.put("url", location);
        ServiceInfo serviceInfo = ServiceInfo.create(JOLOKIA_ZEROCONF_TYPE,
                name, port, weight, priority, persistent, props);
        System.out.println("info: " + serviceInfo);
        return serviceInfo;
    }

    public static JmDNS createJmDNS(String hostName) throws IOException {
        System.out.println("Connecting to hostName: " + hostName);
        return JmDNS.create(hostName);
    }
}
