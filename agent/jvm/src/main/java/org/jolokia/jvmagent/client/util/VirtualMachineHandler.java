package org.jolokia.jvmagent.client.util;

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
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A handler for dealing with <code>VirtualMachine</code> without directly referencing internally
 * the class type. Reflection has been removed due to illegal access in jdk 9+ but this class remains useful to force a
 * good lookup of the com.sun.tools.attach.* classes before referencing / accessing them.
 *
 * @author roland
 * @since 12.08.11
 */
public class VirtualMachineHandler {

    private OptionsAndArgs options;

    /**
     * Constructor with options
     *
     * @param pOptions options for getting e.g. the process id to attach to
     *
     */
    public VirtualMachineHandler(OptionsAndArgs pOptions) {
        options = pOptions;
    }

    /**
     * Lookup and create a {@link com.sun.tools.attach.VirtualMachine} directly or via reflection. First, a direct
     * lookup via {@link Class#forName(String)} is done, which will succeed for JVM on OS X, since tools.jar
     * is bundled there together with classes.zip. Next, tools.jar is tried to be found (by examine <code>java.home</code>)
     * and an own classloader is created for looking up the VirtualMachine.
     *
     * If lookup fails, a message is printed out (except when '--quiet' is provided)
     *
     * @return the create virtual machine of <code>null</code> if none could be created
     */
    public Object attachVirtualMachine() {
        if (options.getPid() == null && options.getProcessPattern() == null) {
            return null;
        }
        Class vmClass = lookupVirtualMachineClass();
        String pid = null;
        try {
            pid = getProcessId(options);
            // Class is now properly loaded, but we can't use reflection due to illegal access in separate modules
            return com.sun.tools.attach.VirtualMachine.attach(pid);
        } catch (IOException | com.sun.tools.attach.AttachNotSupportedException | IllegalArgumentException e) {
            throw new ProcessingException(getPidErrorMesssage(pid, e.getClass().getSimpleName(), vmClass), e, options);
        }
    }

    private String getPidErrorMesssage(String pid, String label, Class vmClass) {
        return pid != null ?
            String.format("Cannot attach to process-ID %s (%s %s).\nSee --help for possible reasons.",
                          pid, label, vmClass.getName()) :
            String.format("%s %s",label, vmClass.getName());
    }

    /**
     * Detach from the virtual machine
     *
     * @param pVm the virtual machine to detach from
     */
    public void detachAgent(Object pVm) {
        try {
            if (pVm instanceof com.sun.tools.attach.VirtualMachine) {
                ((com.sun.tools.attach.VirtualMachine) pVm).detach();
            }
        } catch (IOException e) {
            throw new ProcessingException("Error while detaching",e, options);
        }
    }

    /**
     * Return a list of all Java processes
     * @return list of java processes
     */
    public List<ProcessDescription> listProcesses() {
        List<ProcessDescription> ret = new ArrayList<ProcessDescription>();
        lookupVirtualMachineClass();

        // Class is now properly loaded, but we can't use reflection due to illegal access in separate modules

        for (com.sun.tools.attach.VirtualMachineDescriptor descriptor : com.sun.tools.attach.VirtualMachine.list()) {
            String id = descriptor.id();
            String display = descriptor.displayName();
            ret.add(new ProcessDescription(id, display));
        }
        return ret;
    }

    /**
     * Filter the process list for a regular expression and returns the description. The process this
     * JVM is running in is ignored. If more than one process or no process is found, an exception
     * is raised.
     *
     * @param pPattern regular expression to match
     * @return a process description of the one process found but never null
     * @throws IllegalArgumentException if more than one or no process has been found.
     */
    public ProcessDescription findProcess(Pattern pPattern) {
        List<ProcessDescription> ret = new ArrayList<ProcessDescription>();
        String ownId = getOwnProcessId();

        for (ProcessDescription desc : listProcesses()) {
            Matcher matcher = pPattern.matcher(desc.getDisplay());
            if (!desc.getId().equals(ownId) && matcher.find()) {
                ret.add(desc);
            }
        }
        if (ret.size() == 1) {
            return ret.get(0);
        } else if (ret.size() == 0) {
            throw new IllegalArgumentException("No attachable process found matching \"" + pPattern.pattern() + "\"");
        } else {
            StringBuilder buf = new StringBuilder();
            for (ProcessDescription desc : ret) {
                buf.append(desc.getId()).append(" (").append(desc.getDisplay()).append("),");
            }
            throw new IllegalArgumentException("More than one attachable process found matching \"" +
                                               pPattern.pattern() + "\": " + buf.substring(0,buf.length()-1));
        }
    }

    // ========================================================================================================

    /**
     * Get the process id, either directly from option's ID or by looking up a regular expression for java process name
     * (but not this java process)
     *
     * @param pOpts used to get eithe the process Id ({@link OptionsAndArgs#getPid()} or the pattern for matching a
     *        process name ({@link OptionsAndArgs#getProcessPattern()})
     * @return the numeric id as string
     * @throws IllegalArgumentException if a pattern is used and no or more than one process name matches.
     */
    private String getProcessId(OptionsAndArgs pOpts) {
        if (pOpts.getPid() != null) {
            return pOpts.getPid();
        } else if (pOpts.getProcessPattern() != null) {
            return findProcess(pOpts.getProcessPattern()).getId();
        } else {
            throw new IllegalArgumentException("No process ID and no process name pattern given");
        }
    }

    // Try to find out own process id. This is platform dependent and works on Sun/Oracl/OpeneJDKs like the
    // whole agent, so it should be safe
    private String getOwnProcessId() {
        // Format of name is : <pid>@<host>
        String name = ManagementFactory.getRuntimeMXBean().getName();
        int endIdx = name.indexOf('@');
        return endIdx != -1 ? name.substring(0,endIdx) : name;
    }

    // lookup virtual machine class
    private Class lookupVirtualMachineClass() {
        try {
            return ToolsClassFinder.lookupClass("com.sun.tools.attach.VirtualMachine");
        } catch (ClassNotFoundException exp) {
            throw new ProcessingException(
                    "Cannot find classes from tools.jar. The heuristics for loading tools.jar which contains\n" +
                    "essential classes (i.e. com.sun.tools.attach.VirtualMachine) for attaching to a running JVM\n" +
                    " ould not locate the necessary jar file.\n" +
                    "\n" +
                    "Please call this launcher with a qualified classpath on the command line like\n" +
                    "\n" +
                    "   java -cp path/to/tools.jar:" + options.getJarFileName() + " org.jolokia.jvmagent.client.AgentLauncher [options] <command> <ppid>\n",
                    exp,
                    options);
        }
    }


}

