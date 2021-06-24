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

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlatformUtils {

    private PlatformUtils() {}

    public static VirtualMachineHandlerOperations createVMAccess(OptionsAndArgs options) {
        String version = System.getProperty("java.specification.version");
        if (version == null || "".equals(version.trim())) {
            // fallback to reflection based access
            return new VirtualMachineHandler(options);
        }
        if (version.contains(".")) {
            version = version.substring(version.lastIndexOf('.') + 1);
        }
        try {
            int v = Integer.parseInt(version);
            if (v <= 8) {
                // Java 8 or earlier, where Attach API classes are located in tools.jar, which has to be on classpath
                // or can be detected by Jolokia relatively to ${java.home}
                return new VirtualMachineHandler(options);
            } else {
                // we can create a small JDK image using:
                //     jlink --add-modules=java.se --output /tmp/small-jdk
                // and run the agent using:
                //     /tmp/small-jdk/bin/java -cp jolokia-jvm.jar org.jolokia.jvmagent.client.AgentLauncher list
                // but such image won't contain jdk.attach Java module. So we have to detect its existence
                try {
                    return new DirectVirtualMachineHandler(options);
                } catch (NoClassDefFoundError e) {
                    throw new ProcessingException("Can't load com.sun.tools.attach.VirtualMachine class." +
                            " Is \"jdk.attach\" standard module available?", e, options);
                }
            }
        } catch (NumberFormatException e) {
            // again, fallback silently
            return new VirtualMachineHandler(options);
        }
    }

    // Try to find out own process id. This is platform dependent and works on Sun/Oracl/OpeneJDKs like the
    // whole agent, so it should be safe
    static String getOwnProcessId() {
        // Format of name is : <pid>@<host>
        String name = ManagementFactory.getRuntimeMXBean().getName();
        int endIdx = name.indexOf('@');
        return endIdx != -1 ? name.substring(0,endIdx) : name;
    }

    public static ProcessDescription findProcess(Pattern pPattern, List<ProcessDescription> processes) {
        List<ProcessDescription> ret = new ArrayList<ProcessDescription>();
        String ownId = PlatformUtils.getOwnProcessId();

        for (ProcessDescription desc : processes) {
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

    /**
     * Get the process id, either directly from option's ID or by looking up a regular expression for java process name
     * (but not this java process)
     *
     * @param pHandler platform-specific way to invoke operations on VM
     * @param pOpts used to get eithe the process Id ({@link OptionsAndArgs#getPid()} or the pattern for matching a
     *        process name ({@link OptionsAndArgs#getProcessPattern()})
     * @return the numeric id as string
     * @throws IllegalArgumentException if a pattern is used and no or more than one process name matches.
     */
    public static String getProcessId(VirtualMachineHandlerOperations pHandler, OptionsAndArgs pOpts) {
        if (pOpts.getPid() != null) {
            return pOpts.getPid();
        } else if (pOpts.getProcessPattern() != null) {
            return pHandler.findProcess(pOpts.getProcessPattern()).getId();
        } else {
            throw new IllegalArgumentException("No process ID and no process name pattern given");
        }
    }

}
