package org.jolokia.jvmagent.client.util;

/*
 * Copyright 2009-2021 Roland Huss
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

import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * <p>A contract for dealing with <code>VirtualMachine</code> on any JDK where <a href="https://docs.oracle.com/en/java/javase/11/docs/api/jdk.attach/com/sun/tools/attach/VirtualMachine.html">
 * Attach API is supported.</a></p>
 *
 * <p>The virtual machine is passed as {@link Object} and dealt with differently in chosen implementation of the
 * contract.</p>
 *
 * @author ggrzybek
 * @since 24.06.2021
 */
public interface VirtualMachineHandlerOperations {

    /**
     * Lookup and create a {@code com.sun.tools.attach.VirtualMachine}
     *
     * @return the create virtual machine of <code>null</code> if none could be created
     * @throws ProcessingException for any problem related to VM attaching. Specific to particular implementation.
     */
    Object attachVirtualMachine() throws ProcessingException;

    /**
     * Detach from the virtual machine
     *
     * @param pVm the virtual machine to detach from
     * @throws ProcessingException for any problem related to VM detaching. Specific to particular implementation.
     */
    void detachAgent(Object pVm) throws ProcessingException;

    /**
     * Return a list of all Java processes
     * @return list of java processes
     */
    List<ProcessDescription> listProcesses();

    /**
     * Filter the process list for a regular expression and returns the description. The process this
     * JVM is running in is ignored. If more than one process or no process is found, an exception
     * is raised.
     *
     * @param pPattern regular expression to match
     * @return a process description of the one process found but never null
     * @throws IllegalArgumentException if more than one or no process has been found.
     */
    ProcessDescription findProcess(Pattern pPattern);

    /**
     * Loads Jolokia agent into the attached VM.
     *
     * @param pVm {@link com.sun.tools.attach.VirtualMachine} access object
     * @param jarFilePath path to Java agent JAR file
     * @param args arguments to {@link com.sun.tools.attach.VirtualMachine#loadAgent(String, String)} method
     */
    void loadAgent(Object pVm, String jarFilePath, String args);

    /**
     * Returns system properties from attached VM.
     *
     * @param pVm {@link com.sun.tools.attach.VirtualMachine} access object
     * @return system properties of the remote VM
     */
    Properties getSystemProperties(Object pVm);

}
