/*
 *
 * Copyright 2016 Roland Huss
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

import java.lang.management.ManagementFactory;
import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.jolokia.client.JolokiaClient;
import org.jolokia.client.JolokiaClientBuilder;
import org.jolokia.client.request.JolokiaReadRequest;
import org.jolokia.client.response.JolokiaResponse;
import org.jolokia.server.core.backend.MBeanServerHandlerMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    public static final Logger LOG = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(Application.class);

        Thread.sleep(1000);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = null;
        for (ObjectInstance ins : server.queryMBeans(null, null)) {
            if (ins.getObjectName().getKeyProperty("type").equals("ServerHandler") && ins.getObjectName().getDomain().equals("jolokia")) {
                name = ins.getObjectName();
                break;
            }
        }

        try {
            System.out.printf("With JMX.newMXBeanProxy%n");
            if (name != null) {
                MBeanServerHandlerMBean bean = JMX.newMXBeanProxy(server, name, MBeanServerHandlerMBean.class);
                System.out.printf("%s%n", bean.mBeanServersInfo());
            } else {
                System.out.println("No MBeanServerHandlerMBean found");
            }
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
        }

        try {
            System.out.printf("With MBeanServer%n");
            if (name != null) {
                System.out.printf("%s%n", server.invoke(name, "mBeanServersInfo", new Object[0], new String[0]));
            } else {
                System.out.println("No MBeanServerHandlerMBean found");
            }
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
        }

        try {
            System.out.printf("Checking Jolokia Client based on HttpClient5%n");
            try (JolokiaClient client = new JolokiaClientBuilder().url("http://localhost:8181/jolokia").build()) {
                JolokiaResponse<JolokiaReadRequest> response = client.execute(new JolokiaReadRequest("java.lang:type=Runtime", "VmVendor"));
                System.out.printf("java.lang:type=Runtime/VmVendor: %s%n", (String) response.getValue());
            }
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
        }
    }

}
