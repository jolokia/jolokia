package org.jolokia.jvmagent.client.command;

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
import java.util.Properties;

import org.jolokia.jvmagent.JvmAgent;
import org.jolokia.jvmagent.client.util.OptionsAndArgs;
import org.jolokia.jvmagent.client.util.VirtualMachineHandlerOperations;

/**
 * Stateless Base command providing helper functions
 *
 * @author roland
 * @since 06.10.11
 */
public abstract class AbstractBaseCommand {
    /**
     * The name of the command as it can be given on the command line
     *
     * @return the name of the command
     */
    abstract String getName();

    /**
     * Execute the command
     *
     * @param pOpts options as given on the command line
     * @param pVm the virtual machine object to operate on (as typeless object)
     * @param pHandler the handler holding VM operation
     * @return 0 in case of a success, 1 otherwise
     *
     * @throws IllegalAccessException if call via reflection fails
     * @throws NoSuchMethodException should not happen since we use well known methods
     * @throws InvocationTargetException exception occured during startup of the agent. You probably need to examine
     *         the stdout of the instrumented process as well for error messages.
      */
    abstract int execute(OptionsAndArgs pOpts, Object pVm, VirtualMachineHandlerOperations pHandler) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException;

    // =======================================================================================================

    /**
     * Execute {@code com.sun.tools.attach.VirtualMachine#loadAgent(String, String)}
     *
     * @param pVm the VirtualMachine object, typeless
     * @param pHandler platform-specific way to invoke operations on VM
     * @param pOpts options from where to extract the agent path and options
     * @param pAdditionalOpts optional additional options to be appended to the agent options. Must be a CSV string.
     */
    protected void loadAgent(Object pVm, VirtualMachineHandlerOperations pHandler, OptionsAndArgs pOpts, String ... pAdditionalOpts) {
        String args = pOpts.toAgentArg();
        if (pAdditionalOpts.length > 0) {
            args = args.length() != 0 ? args + "," + pAdditionalOpts[0] : pAdditionalOpts[0];
        }
        pHandler.loadAgent(pVm, pOpts.getJarFilePath(), args.length() > 0 ? args : null);
    }

    /**
     * Check whether an agent is registered by checking the existance of the system property
     * {@link JvmAgent#JOLOKIA_AGENT_URL}. This can be used to check, whether a Jolokia agent
     * has been already attached and started. ("start" will set this property, "stop" will remove it).
     *
     * @param pVm the {@link com.sun.tools.attach.VirtualMachine}, but typeless
     * @param pHandler platform-specific way to invoke operations on VM
     * @return the agent URL if it is was set by a previous 'start' command.
     */
    protected String checkAgentUrl(Object pVm, VirtualMachineHandlerOperations pHandler) {
        return checkAgentUrl(pVm, pHandler, 0);
    }

    /**
     * Check whether an agent is registered by checking the existance of the system property
     * {@link JvmAgent#JOLOKIA_AGENT_URL}. This can be used to check, whether a Jolokia agent
     * has been already attached and started. ("start" will set this property, "stop" will remove it).
     *
     * @param pVm the {@link com.sun.tools.attach.VirtualMachine}, but typeless
     * @param pHandler platform-specific way to invoke operations on VM
     * @param delayInMs wait that many ms before fetching the properties
     ** @return the agent URL if it is was set by a previous 'start' command.
     */
    protected String checkAgentUrl(Object pVm, VirtualMachineHandlerOperations pHandler, int delayInMs) {
        if (delayInMs != 0) {
            try {
                Thread.sleep(delayInMs);
            } catch (InterruptedException e) {
                // just continue
            }
        }
        Properties systemProperties = getAgentSystemProperties(pVm, pHandler);
        return systemProperties.getProperty(JvmAgent.JOLOKIA_AGENT_URL);
    }

    /**
     * Execute {@link com.sun.tools.attach.VirtualMachine#getSystemProperties()} via reflection
     * @param pVm the VirtualMachine object, typeless
     * @param pHandler platform-specific way to invoke operations on VM
     * @return the system properties
     */
    protected Properties getAgentSystemProperties(Object pVm, VirtualMachineHandlerOperations pHandler) {
        return pHandler.getSystemProperties(pVm);
    }

    /**
     * Get a description of the process attached, either the numeric id only or, if a pattern is given,
     * the pattern and the associated PID
     *
     * @param pHandler handler for looking up the process in case of a pattern lookup
     * @param pOpts options from where to take the PID or pattern
     * @return a description of the process
     */
    protected String getProcessDescription(VirtualMachineHandlerOperations pHandler, OptionsAndArgs pOpts) {
        if (pOpts.getPid() != null) {
            return "PID " + pOpts.getPid();
        } else if (pOpts.getProcessPattern() != null) {
            StringBuffer desc = new StringBuffer("process matching \"")
                    .append(pOpts.getProcessPattern().pattern())
                    .append("\"");
            desc.append(" (PID: ")
                    .append(pHandler.findProcess(pOpts.getProcessPattern()).getId())
                    .append(")");
            return desc.toString();
        } else {
            return "(null)";
        }
    }
}
