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
import java.util.Formatter;
import java.util.List;

import org.jolokia.jvmagent.client.util.*;

/**
 * List all available Java processes
 *
 * @author roland
 * @since 06.10.11
 */
public class ListCommand extends AbstractBaseCommand {

    /** {@inheritDoc} */
    @Override
    String getName() {
        return "list";
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("PMD.SystemPrintln")
    int execute(OptionsAndArgs pOpts, Object pVm, VirtualMachineHandlerOperations pHandler) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        List<ProcessDescription> vmDescriptors = pHandler.listProcesses();
        for (ProcessDescription descriptor : vmDescriptors) {
            Formatter formatter = new Formatter().format("%7.7s   %-100.100s",
                                                         stripNewline(descriptor.getId()),
                                                         stripNewline(descriptor.getDisplay()));
            System.out.println(formatter.toString().trim());
        }
        return 0;
    }

    // String any newline at the end of the string
    private String stripNewline(String pText) {
        return pText != null ? pText.trim() : "";
    }

}
