/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jolokia.forge;

import java.io.File;
import java.util.List;

import javax.inject.Inject;

import org.jboss.forge.parser.xml.Node;
import org.jboss.forge.parser.xml.XMLParser;
import org.jboss.forge.project.Project;
import org.jboss.forge.project.dependencies.Dependency;
import org.jboss.forge.project.dependencies.DependencyBuilder;
import org.jboss.forge.project.facets.DependencyFacet;
import org.jboss.forge.project.facets.FacetNotFoundException;
import org.jboss.forge.resources.*;
import org.jboss.forge.shell.Shell;
import org.jboss.forge.shell.ShellPrompt;
import org.jboss.forge.shell.plugins.*;

import static org.jboss.forge.shell.ShellColor.*;

/**
 *
 */
@Alias("jolokia")
public class JolokiaForgePlugin implements Plugin {

    private final static String JOLOKIA_SERVLET_CLASS = "org.jolokia.http.AgentServlet";

    @Inject
    private Shell shell;

    @Inject
    private Project project;

    @Inject
    private XMLParser parser;

    @Inject
    private ShellPrompt prompt;

    @DefaultCommand(help = "Overview over Jolokia setup options")
    public void defaultCommand(@PipeIn String in, PipeOut out) {
        out.println("Jolokia Forge Plugin\n");
        out.println("Available commands:");
        out.println("    setup - Add a Jolokia servlet to web.xml");

        if (getWebXmlNode() == null) {
            out.println();
            out.println("NOTE: Jolokia is only available for Maven Web-Modules (which is not the case for this module)");
        }
    }

    @Command(help = "Setup Jolokia for a Web-Module")
    public void setup(PipeOut out, 
                      @Option(required=false, name="context-root", description="Context root for the Jolokia servlet") String context){
        updateDependencies();
        updateWebXml(out, context);
    }

    private void updateDependencies() {
        DependencyFacet dependencyFacet = project.getFacet(DependencyFacet.class);
        Dependency depPattern = DependencyBuilder.create()
                                                 .setGroupId("org.jolokia")
                                                 .setArtifactId("jolokia-core");
        List<Dependency> versions = dependencyFacet.resolveAvailableVersions(depPattern);
        Dependency dependency = shell.promptChoiceTyped("What version do you want to install ?", versions);
        dependencyFacet.addDirectDependency(dependency);
    }

    private void updateWebXml(PipeOut out, String context) {
        Node webXml = getWebXmlNode();
        if (webXml == null) {
            out.println(RED, "Jolokia can be only used for a Web-Module");
            return;
        }

        List<Node> servlets = webXml.get("servlet/servlet-class=" + JOLOKIA_SERVLET_CLASS);
        if (servlets.size() != 0) {
            out.println(YELLOW,"Jolokia servlet already registered, leaving web.xml untouched");
            return;
        }

        Node servlet = webXml.createChild("servlet");
        servlet.createChild("servlet-name=jolokia-agent");
        servlet.createChild("servlet-class=" + JOLOKIA_SERVLET_CLASS);
        servlet.createChild("load-on-startup=1");

        String jolokiaContext = context != null ? context : "/jolokia";
        if (!jolokiaContext.startsWith("/")) {
            jolokiaContext = "/" + jolokiaContext;
        }
        if (!jolokiaContext.endsWith("/*")) {
            jolokiaContext = jolokiaContext.endsWith("/") ? jolokiaContext + "*" : jolokiaContext + "/*";                  
        }
        Node servletMapping = webXml.createChild("servlet-mapping");
        servletMapping.createChild("servlet-name=jolokia-agent");
        Node urlPattern = servletMapping.createChild("url-pattern");
        urlPattern.text(jolokiaContext);

        saveWebXml(webXml);
    }

    // ================================================================================

    private Node getWebXmlNode() {
        try {
            FileResource<?> webXML = getWebXmlAsFileResource();
            if (webXML != null) {
                return parser.parse(webXML.getResourceInputStream());
            } else {
                return null;
            }
        } catch (FacetNotFoundException exp) {
            // Facet
            return null;
        }
    }

    private void saveWebXml(Node pWebXml) {
        String file = parser.toXMLString(pWebXml);
        FileResource<?> fileResource = getWebXmlAsFileResource();
        fileResource.setContents(file.toCharArray());
    }

    private FileResource<?> getWebXmlAsFileResource() {
        try {
            DirectoryResource dir = project.getProjectRoot().getChildDirectory("src" + File.separator + "main" + File.separator + "webapp");
            if (!dir.exists()) {
                return null;
            }
            FileResource<?> resource = (FileResource<?>) dir.getChild("WEB-INF/web.xml");
            if (!resource.exists()) {
                return null;
            }
            return resource;
        } catch (ResourceException exp) {
            // No such file ...
            return null;
        }
    }


    // Works for Maven only
    DirectoryResource getWebRootDirectory() {
        return project.getProjectRoot().getChildDirectory("src" + File.separator + "main" + File.separator + "webapp");
    }

}
