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
package org.jolokia.it.osgi;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import javax.management.ObjectName;

import jakarta.servlet.ServletContext;
import org.jolokia.json.JSONObject;
import org.jolokia.json.parser.JSONParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class PaxWebWithOSGiAgentIntegrationTest extends AbstractOsgiTestBase {

    public static final Logger LOG = LoggerFactory.getLogger(PaxWebWithOSGiAgentIntegrationTest.class);

    @Configuration
    public Option[] configure() {
        Option[] options = combine(baseConfigure(), defaultLoggingConfig("DEBUG"));
        options = combine(options, paxWebWhiteboard());
        options = combine(options, log4jLogService());
        return combine(options, jolokiaOsgi());
    }

    @Test
    public void waitForJolokiaServlet() throws Exception {
        ServiceTracker<?, ?> tracker = new ServiceTracker<>(context,
            context.createFilter("(&(objectClass=jakarta.servlet.ServletContext)(osgi.web.contextpath=/jolokia))"), null);
        tracker.open();
        ServletContext jolokiaContext = (ServletContext) tracker.waitForService(5000);
        assertNotNull(jolokiaContext);

        HttpRequest request = HttpRequest.newBuilder().uri(new URI("http://localhost:8080/jolokia/version")).GET().build();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JSONObject version = (JSONObject) new JSONParser().parse(response.body());
        assertEquals(System.getProperty("version.jolokia"), ((JSONObject) version.get("value")).get("agent"));
    }

    @Test
    public void readSomeAttributes() throws Exception {
        ServiceTracker<?, ?> tracker = new ServiceTracker<>(context,
            context.createFilter("(&(objectClass=jakarta.servlet.ServletContext)(osgi.web.contextpath=/jolokia))"), null);
        tracker.open();
        ServletContext jolokiaContext = (ServletContext) tracker.waitForService(5000);
        assertNotNull(jolokiaContext);

        HttpRequest request = HttpRequest.newBuilder().uri(new URI("http://localhost:8080/jolokia/read/java.lang:type=Runtime/Name")).GET().build();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JSONObject res = (JSONObject) new JSONParser().parse(response.body());
        assertEquals(ManagementFactory.getPlatformMBeanServer().getAttribute(new ObjectName("java.lang:type=Runtime"), "Name"), res.get("value"));
    }

}
