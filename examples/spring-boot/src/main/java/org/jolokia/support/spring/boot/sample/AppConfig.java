/*
 * Copyright 2009-2023 Roland Huss
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

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import javax.management.ObjectName;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.http.AgentServlet;
import org.jolokia.support.jmx.JolokiaMBeanServerUtil;
import org.jolokia.support.spring.boot.sample.mbeans.Example;
import org.jolokia.support.spring.boot.sample.mbeans.Jolokia;
import org.jolokia.support.spring.boot.sample.mbeans.SpringExample;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.PathResource;
import org.springframework.http.CacheControl;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.jmx.export.annotation.AnnotationJmxAttributeSource;
import org.springframework.jmx.export.assembler.MetadataMBeanInfoAssembler;
import org.springframework.jmx.export.metadata.JmxAttributeSource;
import org.springframework.jmx.export.naming.MetadataNamingStrategy;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class AppConfig implements WebMvcConfigurer, WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    public static final Logger LOG = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    public ServletRegistrationBean<AgentServlet> jolokia() {
        ServletRegistrationBean<AgentServlet> jolokiaServlet = new ServletRegistrationBean<>(new AgentServlet(), "/jolokia/*");
        jolokiaServlet.setLoadOnStartup(0);
        jolokiaServlet.setAsyncSupported(true);
        jolokiaServlet.setInitParameters(Map.of(ConfigKey.DEBUG.getKeyValue(), "true"));
        jolokiaServlet.setInitParameters(Map.of(ConfigKey.AGENT_DESCRIPTION.getKeyValue(), "Spring Servlet Jolokia Agent"));
        return jolokiaServlet;
    }

    @Bean
    public ApplicationListener<ApplicationContextEvent> contextListener() {
        return event -> {
            if (event instanceof ContextRefreshedEvent) {
                LOG.info("Context refreshed: {}", event.getSource().getDisplayName());
                try {
                    JolokiaMBeanServerUtil.registerMBean(new Example(), new ObjectName("jolokia.example:type=Standard"));
                    JolokiaMBeanServerUtil.registerMBean(new Jolokia(), new ObjectName("jolokia.example:type=JsonMBean"));
                } catch (Exception ignored) {/* ignored */}
            }
        };
    }

    @Bean
    public MBeanExporter jmxExporter() {
        MBeanExporter exporter = new MBeanExporter();
        exporter.setAutodetect(true);
        JmxAttributeSource jmxAttributeSource = new AnnotationJmxAttributeSource();
        exporter.setAssembler(new MetadataMBeanInfoAssembler(jmxAttributeSource));
        exporter.setNamingStrategy(new MetadataNamingStrategy(jmxAttributeSource));
        return exporter;
    }

    @Bean
    public SpringExample managedResource() {
        return new SpringExample();
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/index.html");
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        URL here = getClass().getResource("/");
        if (here != null && "file".equals(here.getProtocol())) {
            File jsPackages = new File(here.getFile(), "../../../../client/javascript-esm/packages");
            registry.addResourceHandler("/js/**")
                .addResourceLocations(new FileSystemResource(new File(jsPackages, "jolokia/dist").getAbsolutePath() + File.separator))
                .addResourceLocations(new FileSystemResource(new File(jsPackages, "jolokia-simple/dist").getAbsolutePath() + File.separator))
                .setCachePeriod(0)
                .setCacheControl(CacheControl.noCache());
            registry.addResourceHandler("/**")
                .addResourceLocations(new FileSystemResource(new File(here.getFile(), "../../src/main/webapp").getAbsolutePath() + File.separator))
                .setCachePeriod(0)
                .setCacheControl(CacheControl.noCache());
        }
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.getSettings().getMimeMappings().add("cjs", "application/javascript");
    }
}
