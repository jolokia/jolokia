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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * A handler for dealing with <code>VirtualMachine</code> without directly referencing internally
 * the class type. All lookup is done via reflection. (Name not changed for compatibility reasons).
 *
 * @author roland
 * @since 12.08.11
 */
class VirtualMachineHandler implements VirtualMachineHandlerOperations {

    private final OptionsAndArgs options;

    /**
     * Constructor with options
     *
     * @param pOptions options for getting e.g. the process id to attach to
     *
     */
    VirtualMachineHandler(OptionsAndArgs pOptions) {
        options = pOptions;
    }

    /**
     * Lookup and create a {@link com.sun.tools.attach.VirtualMachine} via reflection. First, a direct
     * lookup via {@link Class#forName(String)} is done, which will succeed for JVM on OS X, since tools.jar
     * is bundled there together with classes.zip. Next, tools.jar is tried to be found (by examine <code>java.home</code>)
     * and an own classloader is created for looking up the VirtualMachine.
     *
     * If lookup fails, a message is printed out (except when '--quiet' is provided)
     *
     * @return the create virtual machine of <code>null</code> if none could be created
     */
    @Override
    public Object attachVirtualMachine() throws ProcessingException {
        if (options.getPid() == null && options.getProcessPattern() == null) {
            return null;
        }
        Class<?> vmClass = lookupVirtualMachineClass();
        String pid = null;
        try {
            Method method = vmClass.getMethod("attach",String.class);
            pid = PlatformUtils.getProcessId(this, options);
            return method.invoke(null, pid);
        } catch (NoSuchMethodException e) {
            throw new ProcessingException("Internal: No method 'attach' found on " + vmClass,e,options);
        } catch (InvocationTargetException e) {
            throw new ProcessingException(getPidErrorMesssage(pid,"InvocationTarget",vmClass),e,options);
        } catch (IllegalAccessException e) {
            throw new ProcessingException(getPidErrorMesssage(pid, "IllegalAccessException", vmClass),e,options);
        } catch (IllegalArgumentException e) {
            throw new ProcessingException("Illegal Argument",e,options);
        }
    }

    private String getPidErrorMesssage(String pid, String label, Class<?> vmClass) {
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
    @Override
    public void detachAgent(Object pVm) {
        try {
            if (pVm != null) {
                Class<?> clazz = pVm.getClass();
                Method method = clazz.getMethod("detach");
                method.setAccessible(true); // on J9 you get IllegalAccessException otherwise.
                method.invoke(pVm);
            }
        } catch (InvocationTargetException e) {
            throw new ProcessingException("Error while detaching",e, options);
        } catch (NoSuchMethodException e) {
            throw new ProcessingException("Error while detaching",e, options);
        } catch (IllegalAccessException e) {
            throw new ProcessingException("Error while detaching",e, options);
        }
    }

    /**
     * Return a list of all Java processes
     * @return list of java processes
     * @throws ProcessingException reflection error
     */
    @Override
    public List<ProcessDescription> listProcesses() {
        List<ProcessDescription> ret = new ArrayList<ProcessDescription>();
        Class<?> vmClass = lookupVirtualMachineClass();
        try {
            Method method = vmClass.getMethod("list");
            List<?> vmDescriptors = (List<?>) method.invoke(null);
            for (Object descriptor : vmDescriptors) {
                Method idMethod = descriptor.getClass().getMethod("id");
                String id = (String) idMethod.invoke(descriptor);
                Method displayMethod = descriptor.getClass().getMethod("displayName");
                String display = (String) displayMethod.invoke(descriptor);
                ret.add(new ProcessDescription(id, display));
            }
            return ret;
        } catch (NoSuchMethodException e) {
            throw new ProcessingException("Error while listing JVM processes", e, options);
        } catch (InvocationTargetException e) {
            throw new ProcessingException("Error while listing JVM processes", e, options);
        } catch (IllegalAccessException e) {
            throw new ProcessingException("Error while listing JVM processes", e, options);
        }
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
    @Override
    public ProcessDescription findProcess(Pattern pPattern) {
        return PlatformUtils.findProcess(pPattern, listProcesses());
    }

    @Override
    public void loadAgent(Object pVm, String jarFilePath, String args) throws ProcessingException {
        Class<?> clazz = pVm.getClass();
        try {
            Method method = clazz.getMethod("loadAgent",String.class, String.class);
            method.invoke(pVm, jarFilePath, args);
        } catch (NoSuchMethodException e) {
            throw new ProcessingException("Error while loading Jolokia agent to a JVM process", e, options);
        } catch (InvocationTargetException e) {
            throw new ProcessingException("Error while loading Jolokia agent to a JVM process", e, options);
        } catch (IllegalAccessException e) {
            throw new ProcessingException("Error while loading Jolokia agent to a JVM process", e, options);
        }
    }

    @Override
    public Properties getSystemProperties(Object pVm) {
        Class<?> clazz = pVm.getClass();
        try {
            Method method = clazz.getMethod("getSystemProperties");
            return (Properties) method.invoke(pVm);
        } catch (NoSuchMethodException e) {
            throw new ProcessingException("Error while getting system properties from a JVM process", e, options);
        } catch (InvocationTargetException e) {
            throw new ProcessingException("Error while getting system properties from a JVM process", e, options);
        } catch (IllegalAccessException e) {
            throw new ProcessingException("Error while getting system properties from a JVM process", e, options);
        }
    }

    // ========================================================================================================

    // lookup virtual machine class
    private Class<?> lookupVirtualMachineClass() {
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

