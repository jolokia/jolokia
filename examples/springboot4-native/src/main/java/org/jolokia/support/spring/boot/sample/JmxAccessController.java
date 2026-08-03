/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.support.spring.boot.sample;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.jolokia.client.JolokiaClient;
import org.jolokia.client.JolokiaClientBuilder;
import org.jolokia.client.request.JolokiaReadRequest;
import org.jolokia.client.response.JolokiaResponse;
import org.jolokia.server.core.backend.MBeanServerHandlerMBean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JmxAccessController {

    @RequestMapping("/jmx")
    String jmxInformation() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = null;
        for (ObjectInstance ins : server.queryMBeans(null, null)) {
            String type = ins.getObjectName().getKeyProperty("type");
            if ("ServerHandler".equals(type) && ins.getObjectName().getDomain().equals("jolokia")) {
                name = ins.getObjectName();
                break;
            }
        }

        try {
            pw.printf("%n=== Access to Jolokia MBean using javax.management.JMX.newMXBeanProxy()%n");
            if (name != null) {
                MBeanServerHandlerMBean bean = JMX.newMXBeanProxy(server, name, MBeanServerHandlerMBean.class);
                pw.printf("%s%n", bean.mBeanServersInfo());
            } else {
                pw.println("No MBeanServerHandlerMBean found");
            }
        } catch (Throwable e) {
            e.printStackTrace(pw);
        }

        try {
            pw.printf("%n=== Access to Jolokia MBean using MBeanServer%n");
            if (name != null) {
                pw.printf("%s%n", server.invoke(name, "mBeanServersInfo", new Object[0], new String[0]));
            } else {
                pw.println("No MBeanServerHandlerMBean found");
            }
        } catch (Throwable e) {
            e.printStackTrace(pw);
        }

        try {
            pw.printf("%n=== Access to Platform MBean using Jolokia Client based on HttpClient5%n");
            try (JolokiaClient client = new JolokiaClientBuilder().url("http://localhost:8181/jolokia").build()) {
                JolokiaResponse<JolokiaReadRequest> response = client.execute(new JolokiaReadRequest("java.lang:type=Runtime", "VmVendor"));
                pw.printf("java.lang:type=Runtime/VmVendor: %s%n", (String) response.getValue());
            }
        } catch (Throwable e) {
            e.printStackTrace(pw);
        }
        pw.close();

        return sw.toString();
    }

}
