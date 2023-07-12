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

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * A handler for dealing with <code>VirtualMachine</code> directly accessing {@link com.sun.tools.attach.VirtualMachine}
 * class available in {@code jdk.attach} module.
 *
 * @author ggrzybek
 * @since 12.08.11
 */
class DirectVirtualMachineHandler implements VirtualMachineHandlerOperations {

    private final OptionsAndArgs options;

    DirectVirtualMachineHandler(OptionsAndArgs options) {
        this.options = options;
    }

    @Override
    public Object attachVirtualMachine() throws ProcessingException {
        if (options.getPid() == null && options.getProcessPattern() == null) {
            return null;
        }
        String pid = PlatformUtils.getProcessId(this, options);
        try {
            return VirtualMachine.attach(pid);
        } catch (AttachNotSupportedException e) {
            throw new ProcessingException(getPidErrorMesssage(pid, "AttachNotSupportedException"), e, options);
        } catch (IOException e) {
            throw new ProcessingException(getPidErrorMesssage(pid, "InvocationTarget"), e, options);
        } catch (IllegalArgumentException e) {
            throw new ProcessingException("Illegal Argument", e, options);
        }
    }

    private String getPidErrorMesssage(String pid, String label) {
        return pid != null ?
                String.format("Cannot attach to process-ID %s (%s %s).\nSee --help for possible reasons.",
                        pid, label, VirtualMachine.class.getName()) :
                String.format("%s %s",label, VirtualMachine.class.getName());
    }

    @Override
    public void detachAgent(Object pVm) throws ProcessingException {
        try {
            ((VirtualMachine) pVm).detach();
        } catch (IOException e) {
            throw new ProcessingException("Error while detaching", e, options);
        }
    }

    @Override
    public List<ProcessDescription> listProcesses() {
        List<ProcessDescription> ret = new ArrayList<ProcessDescription>();
        for (VirtualMachineDescriptor descriptor : VirtualMachine.list()) {
            ret.add(new ProcessDescription(descriptor.id(), descriptor.displayName()));
        }
        return ret;
    }

    @Override
    public ProcessDescription findProcess(Pattern pPattern) {
        return PlatformUtils.findProcess(pPattern, listProcesses());
    }

    @Override
    public void loadAgent(Object pVm, String jarFilePath, String args) {
        try {
            ((VirtualMachine) pVm).loadAgent(jarFilePath, args);
        } catch (AgentLoadException e) {
            throw new ProcessingException("Error while loading Jolokia agent to a JVM process", e, options);
        } catch (AgentInitializationException e) {
            throw new ProcessingException("Error while loading Jolokia agent to a JVM process", e, options);
        } catch (IOException e) {
            throw new ProcessingException("Error while loading Jolokia agent to a JVM process", e, options);
        }
    }

    @Override
    public Properties getSystemProperties(Object pVm) {
        try {
            return ((VirtualMachine) pVm).getSystemProperties();
        } catch (IOException e) {
            throw new ProcessingException("Error while getting system properties from a JVM process", e, options);
        }
    }

}
