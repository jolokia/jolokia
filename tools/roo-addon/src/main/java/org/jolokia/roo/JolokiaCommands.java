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

package org.jolokia.roo;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;

import org.apache.felix.scr.annotations.*;
import org.springframework.roo.metadata.MetadataService;
import org.springframework.roo.process.manager.FileManager;
import org.springframework.roo.process.manager.MutableFile;
import org.springframework.roo.project.*;
import org.springframework.roo.shell.*;
import org.springframework.roo.support.util.*;
import org.w3c.dom.*;

/**
 * Roo commands for setting up Jolokia within a Web-Project
 *
 * @author roland
 * @since 10.02.11
 */
@Component
@Service
public class JolokiaCommands implements CommandMarker {

	private Logger log = Logger.getLogger(getClass().getName());

    // Reference to use roo services
    @Reference private ProjectOperations projectOperations;
    @Reference private FileManager fileManager;
	@Reference private PathResolver pathResolver;
	@Reference private MetadataService metadataService;

    @CliAvailabilityIndicator("jolokia setup")
	public boolean isJolokiaAvailable() {
        ProjectMetadata project = (ProjectMetadata) metadataService.get(ProjectMetadata.getProjectIdentifier());
		if (project == null) {
			return false;
		}

		// Do not permit installation unless they have a web project (as per ROO-342)
		if (!fileManager.exists(pathResolver.getIdentifier(Path.SRC_MAIN_WEBAPP, "/WEB-INF/web.xml"))) {
			return false;
		}
        return true;
	}

    @CliCommand(value = "jolokia setup", help = "Adds/Updates dependencies and a servlet \"/jolokia\" for accessing the Jolokia agent")
	public void setupJolokia(
            @CliOption(key = "addPolicy",
                       mandatory = false,
                       specifiedDefaultValue = "true",
                       unspecifiedDefaultValue = "false",
                       help = "Add a sample access policy descriptor") String addPolicyDescriptor,
            @CliOption(key = "addJsr60Proxy",
                       mandatory = false,
                       specifiedDefaultValue = "true",
                       unspecifiedDefaultValue = "false",
                       help = "Setup agent for handling JSR-160 proxy requests") String addJsr160Proxy,
            @CliOption(key = "addDefaultInitParams",
                       mandatory = false,
                       specifiedDefaultValue = "true",
                       unspecifiedDefaultValue = "false",
                       help = "Add init parameters with the default values for the Jolokia-Servlet") String addDefaultInitParams
            ) {
		// Parse the configuration.xml file
		Element configuration = XmlUtils.getConfiguration(getClass());

        // Update dependencies
        updateDependencies(configuration,Boolean.parseBoolean(addJsr160Proxy));

        // Update web.xml
        updateWebXml(configuration, Boolean.parseBoolean(addJsr160Proxy), Boolean.parseBoolean(addDefaultInitParams));

        // (Optional) Add jolokia-access.xml
        if (Boolean.parseBoolean(addPolicyDescriptor)) {
            updateJolokiaAccessXml();
        }
	}

    // =================================================================================================

    private void updateDependencies(Element configuration, boolean pAddJsr160Proxy) {
        ProjectMetadata project = (ProjectMetadata) metadataService.get(ProjectMetadata.getProjectIdentifier());
		List<Element> dependencies =
                XmlUtils.findElements("/configuration/jolokia/dependencies/dependency", configuration);
        if (pAddJsr160Proxy) {
            dependencies.addAll(
                    XmlUtils.findElements("/configuration/jolokia/jsr160Proxy/dependency",configuration)
            );
        }
        for (Element dependencyElement : dependencies) {
            Dependency dep = new Dependency(dependencyElement);
            Set<Dependency> givenDeps = project.getDependenciesExcludingVersion(dep);
            for (Dependency given : givenDeps) {
                if (!given.getVersionId().equals(dep.getVersionId())) {
                    log.info("Updating " + dep.getGroupId() + ":" + dep.getArtifactId() + " from version " + given.getVersionId() + " to " + dep.getVersionId());
                    projectOperations.removeDependency(given);
                }
            }
            projectOperations.addDependency(dep);
        }
	}

    private void updateWebXml(Element pConfiguration, boolean pAddjsr160proxy, boolean pAdddefaultinitparams) {
        InputStream is = null;
        try {
            String webXml = pathResolver.getIdentifier(Path.SRC_MAIN_WEBAPP, "WEB-INF/web.xml");
            if (fileManager.exists(webXml)) {
                MutableFile mutableWebXml = fileManager.updateFile(webXml);
                is = mutableWebXml.getInputStream();
                Document webXmlDoc = XmlUtils.getDocumentBuilder().parse(is);

                // Adapt web.xml
                updateServletDefinition(pConfiguration, pAdddefaultinitparams, webXmlDoc);

                // (Optional) Add JSR-160 proxy handler
                if (pAddjsr160proxy) {
                    updateJsr160Proxy(pConfiguration,webXmlDoc);
                }

                XmlUtils.writeXml(mutableWebXml.getOutputStream(), webXmlDoc);
            } else {
                throw new IllegalStateException("Could not acquire " + webXml);
            }
        } catch (Exception exp) {
            throw new IllegalStateException(exp);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) { /* silently ignore */ }
            }
        }
    }

    private void updateServletDefinition(Element pConfiguration, boolean pAddDefaultInitParams, Document webXmlDoc) {
        WebXmlUtils.WebXmlParam initParams[] = new WebXmlUtils.WebXmlParam[0];
        if (pAddDefaultInitParams) {
            initParams = getDefaultParams(pConfiguration);
        }
        WebXmlUtils.addServlet("jolokia", "org.jolokia.http.AgentServlet", "/jolokia/*", 10,
                               webXmlDoc, "Jolokia Agent", initParams);
    }

    private WebXmlUtils.WebXmlParam[] getDefaultParams(Element configuration) {
		List<Element> initParams =
                XmlUtils.findElements("/configuration/jolokia/initParams/init-param", configuration);
        ArrayList<WebXmlUtils.WebXmlParam> ret = new ArrayList<WebXmlUtils.WebXmlParam>();
        for (Element initParam : initParams) {
            ret.add(new WebXmlUtils.WebXmlParam(
                    getTextContent(initParam, "param-name"),
                    getTextContent(initParam,"param-value")));
//                    getTextContent(initParam,"description")
//                    ));

        }
        return ret.toArray(new WebXmlUtils.WebXmlParam[ret.size()]);
    }

    private void updateJolokiaAccessXml() {
        String destination = pathResolver.getIdentifier(Path.SRC_MAIN_RESOURCES, "jolokia-access.xml");
        if (!fileManager.exists(destination)) {
            try {
                FileCopyUtils.copy(TemplateUtils.getTemplate(getClass(), "jolokia-access.xml"), fileManager.createFile(destination).getOutputStream());
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        }
    }


    private void updateJsr160Proxy(Element configuration, Document webXmlDoc) {
        String paramName = getJsr160InitArg(configuration,"param-name");
        String paramValue = getJsr160InitArg(configuration,"param-value");

        Element servlet = XmlUtils.findFirstElement("/web-app/servlet[servlet-name = 'jolokia']", webXmlDoc.getDocumentElement());
        if (servlet == null) {
            throw new IllegalArgumentException("Internal: No servlet 'jolokia' found in WEB-INF/web.xml");
        }
        Element initParam = XmlUtils.findFirstElement("init-param[param-name = '" + paramName + "']" ,  servlet);
        if (initParam == null) {
            // Create missing init param
            initParam = new XmlElementBuilder("init-param",webXmlDoc)
                    .addChild(new XmlElementBuilder("param-name",webXmlDoc).setText(paramName).build())
                    .addChild(new XmlElementBuilder("param-value",webXmlDoc).setText(paramValue).build())
                    .build();

            Element lastElement = XmlUtils.findFirstElement("load-on-startup",servlet);
            if (lastElement != null) {
                servlet.insertBefore(initParam, lastElement);
            } else {
                servlet.appendChild(initParam);
            }
        } else {
            Element value = XmlUtils.findFirstElement("param-value",initParam);
            value.setTextContent(paramValue);
        }
    }

    private String getJsr160InitArg(Element configuration, String pWhat) {
        return XmlUtils.findFirstElement("/configuration/jolokia/jsr160Proxy/init-param/" + pWhat, configuration).getTextContent();
    }

    private void addLineBreak(Node pRootElement, Node pBeforeThis, Document pWebXmlDoc) {
        pRootElement.insertBefore(pWebXmlDoc.createTextNode("\n    "), pBeforeThis);
        pRootElement.insertBefore(pWebXmlDoc.createTextNode("\n    "), pBeforeThis);
    }


    private String getTextContent(Element initParam,String tag) {
        return initParam.getElementsByTagName(tag).item(0).getTextContent();
    }


}
