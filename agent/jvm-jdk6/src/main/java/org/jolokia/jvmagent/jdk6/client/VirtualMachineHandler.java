package org.jolokia.jvmagent.jdk6.client;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A handler for dealing with <code>VirtualMachine</code> without directly referencing internally
 * the class type. All lookup is done via reflection.
 *
 * @author roland
 * @since 12.08.11
 */
public class VirtualMachineHandler {

    private OptionsAndArgs options;

    /**
     * Constructor
     *
     * @param pOptions options for
     *
     */
    public VirtualMachineHandler(OptionsAndArgs pOptions) {
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
    public Object attachVirtualMachine() {
        if (options.getPid() == null) {
            return null;
        }
        Class vmClass = lookupVirtualMachineClass();

        try {
            Method method = vmClass.getMethod("attach",String.class);
            return method.invoke(null,options.getPid());
        } catch (NoSuchMethodException e) {
            throw new ProcessingException("Internal: No method 'attach' found on " + vmClass,e,options);
        } catch (InvocationTargetException e) {
            throw new ProcessingException("InvocationTarget " + vmClass,e,options);
        } catch (IllegalAccessException e) {
            throw new ProcessingException("IllegalAccess to " + vmClass,e,options);
        }
    }

    /**
     * Detach from the virtual machine
     *
     * @param pVm the virtual machine to detach from
     */
    public void detachAgent(Object pVm) {
        try {
            if (pVm != null) {
                Class clazz = pVm.getClass();
                Method method = clazz.getMethod("detach");
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
     * @throws NoSuchMethodException reflection error
     * @throws InvocationTargetException reflection error
     * @throws IllegalAccessException reflection error
     */
    public List<ProcessDesc> listProcesses() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        List<ProcessDesc> ret = new ArrayList<ProcessDesc>();
        Class vmClass = lookupVirtualMachineClass();
        Method method = vmClass.getMethod("list");
        List vmDescriptors = (List) method.invoke(null);
        for (Object descriptor : vmDescriptors) {
            Method idMethod = descriptor.getClass().getMethod("id");
            String id = (String) idMethod.invoke(descriptor);
            Method displayMethod = descriptor.getClass().getMethod("displayName");
            String display = (String) displayMethod.invoke(descriptor);
            ret.add(new ProcessDesc(id, display));
        }
        return ret;
    }

    // ========================================================================================================

    // Try hard to load the VirtualMachine class
    Class lookupVirtualMachineClass() {
        try {
            String vmClassName = "com.sun.tools.attach.VirtualMachine";
            try {
                return Class.forName(vmClassName);
            } catch (ClassNotFoundException exp) {
                return lookupInToolsJar(vmClassName);
            }
        } catch (Exception exp) {
            throw new ProcessingException(
    "Cannot find classes from tools.jar. The heuristics for loading tools.jar which contains\n" +
    "essential classes for attaching to a running JVM could locate the necessary jar file.\n" +
    "\n" +
    "Please call this launcher with a qualified classpath on the command line like\n" +
    "\n" +
    "   java -cp path/to/tools.jar:" + options.getJarFileName() + " " + AgentLauncher.class.getName() + " [options] <command> <ppid>\n",
    exp,options);
        }
    }

    // Go hunting for tools.jar
    private Class lookupInToolsJar(String pVmClassName) throws MalformedURLException, ClassNotFoundException {
        // Try to look up tools.jar within $java.home, otherwise give up
        String javaHome = System.getProperty("java.home");
        String extraInfo;
        if (javaHome != null) {
            extraInfo = "JAVA_HOME is " + javaHome;
            File[] toolsJars = new File[] {
                    new File(javaHome + "/../lib/tools.jar"),
                    new File(javaHome + "/lib/tools.jar")
            };
            for (File toolsJar : toolsJars) {
                if (toolsJar.exists()) {
                    ClassLoader loader = new URLClassLoader(new URL[] {toolsJar.toURI().toURL() },AgentLauncher.class.getClassLoader());
                    return loader.loadClass(pVmClassName);
                }
            }
        } else {
            extraInfo = "No JAVA_HOME set";
        }
        throw new ClassNotFoundException("No tools.jar found (" + extraInfo + ")");
    }


    // =========================================================================================
    // Process descriptions

    static class ProcessDesc {
        private String id;
        private String display;

        public ProcessDesc(String pId, String pDisplay) {
            id = pId;
            display = pDisplay;
        }

        public String getId() {
            return id;
        }

        public String getDisplay() {
            return display;
        }
    }
}

