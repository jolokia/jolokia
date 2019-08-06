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

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.VirtualMachine;
import org.jolokia.jvmagent.JvmAgent;
import org.jolokia.jvmagent.client.util.OptionsAndArgs;
import org.jolokia.jvmagent.client.util.VirtualMachineHandler;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

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
     * @throws AgentException if call via reflection fails, or an exception occurred during startup of the agent. You probably need to examine
     *         the stdout of the instrumented process as well for error messages.
      */
    abstract int execute(OptionsAndArgs pOpts, Object pVm,VirtualMachineHandler pHandler) throws AgentException;

    // =======================================================================================================

    /**
     * Execute {@link com.sun.tools.attach.VirtualMachine#loadAgent(String, String)} directly or via reflection
     *
     * @param pVm the VirtualMachine object, typeless
     * @param pOpts options from where to extract the agent path and options
     * @param pAdditionalOpts optional additional options to be appended to the agent options. Must be a CSV string.
     */
    protected void loadAgent(Object pVm, OptionsAndArgs pOpts,String ... pAdditionalOpts) throws AgentException {
        String agent = pOpts.getJarFilePath();
        String options = pOpts.toAgentArg();
        if (pAdditionalOpts.length > 0) {
            options = options.length() != 0 ? options + "," + pAdditionalOpts[0] : pAdditionalOpts[0];
        }
        if ("".equals(options)) {
            options = null;
        }

        try {
            if (pVm instanceof VirtualMachine) {
                ((VirtualMachine) pVm).loadAgent(agent, options);
            } else {
                Class clazz = pVm.getClass();
                Method method = clazz.getMethod("loadAgent",String.class, String.class);
                method.invoke(pVm, agent, options);
            }
        } catch (AgentLoadException | AgentInitializationException | IOException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new AgentException("Failed to load agent", e);
        }
    }

    /**
     * Check whether an agent is registered by checking the existance of the system property
     * {@link JvmAgent#JOLOKIA_AGENT_URL}. This can be used to check, whether a Jolokia agent
     * has been already attached and started. ("start" will set this property, "stop" will remove it).
     *
     * @param pVm the {@link com.sun.tools.attach.VirtualMachine}, but typeless
     * @return the agent URL if it is was set by a previous 'start' command.
     */
    protected String checkAgentUrl(Object pVm) throws AgentException {
        return checkAgentUrl(pVm, 0);
    }

    /**
     * Check whether an agent is registered by checking the existance of the system property
     * {@link JvmAgent#JOLOKIA_AGENT_URL}. This can be used to check, whether a Jolokia agent
     * has been already attached and started. ("start" will set this property, "stop" will remove it).
     *
     * @param pVm the {@link com.sun.tools.attach.VirtualMachine}, but typeless
     * @param delayInMs wait that many ms before fetching the properties
     ** @return the agent URL if it is was set by a previous 'start' command.
     */
    protected String checkAgentUrl(Object pVm, int delayInMs) throws AgentException {
        if (delayInMs != 0) {
            try {
                Thread.sleep(delayInMs);
            } catch (InterruptedException e) {
                // just continue
            }
        }
        Properties systemProperties = getAgentSystemProperties(pVm);
        return systemProperties.getProperty(JvmAgent.JOLOKIA_AGENT_URL);
    }

    /**
     * Execute {@link com.sun.tools.attach.VirtualMachine#getSystemProperties()} directly or via reflection
     * @param pVm the VirtualMachine object, typeless
     * @return the system properties
     */
    protected Properties getAgentSystemProperties(Object pVm) throws AgentException {
        Properties systemProperties;

        try {
            if (pVm instanceof VirtualMachine) {
                systemProperties = ((VirtualMachine) pVm).getSystemProperties();
            } else {
                Class clazz = pVm.getClass();
                Method method = clazz.getMethod("getSystemProperties");
                systemProperties = (Properties) method.invoke(pVm);
            }
        } catch (IOException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new AgentException("Failed to get agent system properties", e);
        }

        return systemProperties;
    }

    /**
     * Get a description of the process attached, either the numeric id only or, if a pattern is given,
     * the pattern and the associated PID
     *
     * @param pOpts options from where to take the PID or pattern
     * @param pHandler handler for looking up the process in case of a pattern lookup
     * @return a description of the process
     */
    protected String getProcessDescription(OptionsAndArgs pOpts, VirtualMachineHandler pHandler) {
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
