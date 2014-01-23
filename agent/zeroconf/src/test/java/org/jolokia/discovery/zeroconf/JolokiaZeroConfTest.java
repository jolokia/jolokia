package org.jolokia.discovery.zeroconf;

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

import org.jolokia.jvmagent.JvmAgent;
import org.jolokia.test.util.EnvTestUtil;
import org.testng.annotations.Test;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.net.InetAddress;
import java.util.Arrays;


public class JolokiaZeroConfTest implements ServiceListener {

    @Test
    public void zeroconf() throws Exception {
        int port = EnvTestUtil.getFreePort();
        try {
            String args = "port=" + port;

            String hostName = System.getenv("HOSTNAME");
            if (hostName == null) {
                hostName = InetAddress.getLocalHost().getHostName();
            }
            if (hostName != null && hostName.length() > 0) {
                args += ",host=" + hostName;
            }

            if (hostName == null) {
                hostName = "localhost";
            }
            JmDNS jmdns = ZeroconfDiscovery.createJmDNS(hostName);
            jmdns.addServiceListener(ZeroconfDiscovery.JOLOKIA_ZEROCONF_TYPE, this);
            JvmAgent.agentmain(args);

            Thread.sleep(2000);

            ServiceInfo[] list = jmdns.list(ZeroconfDiscovery.JOLOKIA_ZEROCONF_TYPE);
            for (ServiceInfo info : list) {
                onServiceInfo(info);
            }
        } finally {
            JvmAgent.agentmain("mode=stop");
        }

    }

    public void serviceAdded(ServiceEvent serviceEvent) {
        System.out.println("ADDED: " + serviceEvent);
        ServiceInfo info = serviceEvent.getInfo();
        onServiceInfo(info);
    }

    protected void onServiceInfo(ServiceInfo info) {
        if (info != null) {
            System.out.println("type: " + info.getType());
            System.out.println("subtype: " + info.getSubtype());
            System.out.println("server: " + info.getServer());
            System.out.println("protocol: " + info.getProtocol());
            System.out.println("port: " + info.getPort());
            System.out.println("domain: " + info.getDomain());
            System.out.println("key: " + info.getKey());
            System.out.println("name: " + info.getName());
            System.out.println("qualifiedName: " + info.getQualifiedName());
            System.out.println("hostAddresses: " + Arrays.asList(info.getHostAddresses()));
            System.out.println("niceTextString: " + info.getNiceTextString());
            System.out.println("urls: " + Arrays.asList(info.getURLs()));
            String path = info.getPropertyString("path");
            System.out.println("path: " + path);
        }
    }

    public void serviceRemoved(ServiceEvent serviceEvent) {
        System.out.println("REMOVED: " + serviceEvent);
    }

    public void serviceResolved(ServiceEvent serviceEvent) {
    }
}
