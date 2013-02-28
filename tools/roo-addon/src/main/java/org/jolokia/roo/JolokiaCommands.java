package org.jolokia.roo;

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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.*;
import org.springframework.roo.metadata.MetadataService;
import org.springframework.roo.process.manager.FileManager;
import org.springframework.roo.process.manager.MutableFile;
import org.springframework.roo.project.*;
import org.springframework.roo.project.maven.Pom;
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

	// Reference to use roo services
    @Reference private ProjectOperations projectOperations;
    @Reference private FileManager fileManager;
	@Reference private PathResolver pathResolver;
	@Reference private MetadataService metadataService;

    @CliAvailabilityIndicator("jolokia setup")
	public boolean isJolokiaAvailable() {
        String module = projectOperations.getFocusedModuleName();
        ProjectMetadata project = (ProjectMetadata) metadataService.get(ProjectMetadata.getProjectIdentifier(module));
		if (project == null) {
			return false;
		}

		// Do not permit installation unless they have a web project
        return fileManager.exists(pathResolver.getIdentifier(Path.SRC_MAIN_WEBAPP.getModulePathId(module), "/WEB-INF/web.xml"));
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

        // Add labs repository if not present
        addRepository(configuration);

        // Update web.xml
        updateWebXml(configuration, Boolean.parseBoolean(addJsr160Proxy), Boolean.parseBoolean(addDefaultInitParams));

        // (Optional) Add jolokia-access.xml
        if (Boolean.parseBoolean(addPolicyDescriptor)) {
            updateJolokiaAccessXml();
        }
	}

    // =================================================================================================

    private void updateDependencies(Element configuration, boolean pAddJsr160Proxy) {
        List<Element> dependencyElements =
                XmlUtils.findElements("/configuration/jolokia/dependencies/dependency", configuration);
        addJsr160Dependencies(configuration,dependencyElements, pAddJsr160Proxy);

        List<Dependency> dependencies = new ArrayList<Dependency>();
        for (Element dependencyElement : dependencyElements) {
            dependencies.add(new Dependency(dependencyElement));
        }
        projectOperations.addDependencies(projectOperations.getFocusedModuleName(), dependencies);
	}

    private void addJsr160Dependencies(Element configuration, List<Element> pDependencyElements, boolean pAddJsr160Proxy) {
        List<Element> pJsr160DepElements = XmlUtils.findElements("/configuration/jolokia/jsr160Proxy/dependency", configuration);
        if (pAddJsr160Proxy) {
            pDependencyElements.addAll(pJsr160DepElements);
        } else {
            // Check, whether there is already a jsr160 dependency present. If so, add it again 
            // so that it gets properly update if an dep update operation is performed.
            Pom pom = projectOperations.getFocusedModule();
            for (Element jsr160DepElement : pJsr160DepElements) {
                Dependency jsr160Dep = new Dependency(jsr160DepElement);
                for (Dependency dep: pom.getDependencies()) {
                    if (dep.hasSameCoordinates(jsr160Dep)) {
                        pDependencyElements.add(jsr160DepElement);
                    }
                }                
            }
        }
    }

    private void addRepository(Element configuration) {
        Pom pom = projectOperations.getFocusedModule();
        // Check whether we are a snapshot version, if so, we are adding our snapshot repository
        List<Element> versions =
                XmlUtils.findElements("/configuration/jolokia/dependencies/dependency/version", configuration);
        boolean isSnapshot = false;
        for (Element version : versions) {
            if (version.getTextContent().matches(".*SNAPSHOT$")) {
                isSnapshot = true;
                break;
            }
        }

        List<Element> repositories =
                isSnapshot ?
                        XmlUtils.findElements("/configuration/jolokia/snapshots-repositories/repository", configuration) :
                        XmlUtils.findElements("/configuration/jolokia/repositories/repository", configuration);

        for (Element repositoryElement : repositories) {
            Repository repository = new Repository(repositoryElement);
            projectOperations.addRepository(pom.getModuleName(),repository);
        }
    }

    private void updateWebXml(Element pConfiguration, boolean pAddjsr160proxy, boolean pAdddefaultinitparams) {
        InputStream is = null;
        try {
            String webXml = pathResolver.getIdentifier(Path.SRC_MAIN_WEBAPP.getModulePathId(projectOperations.getFocusedModuleName()), 
                                                       "WEB-INF/web.xml");
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
        String destination = pathResolver.getIdentifier(Path.SRC_MAIN_RESOURCES.getModulePathId(projectOperations.getFocusedModuleName()), 
                                                        "jolokia-access.xml");
        if (!fileManager.exists(destination)) {
            try {
                IOUtils.copy(FileUtils.getInputStream(getClass(), "jolokia-access.xml"),
                             fileManager.createFile(destination).getOutputStream());
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
