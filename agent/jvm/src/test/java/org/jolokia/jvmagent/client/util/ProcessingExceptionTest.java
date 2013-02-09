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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.jolokia.jvmagent.client.command.CommandDispatcher;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 06.10.11
 */
public class ProcessingExceptionTest {

    @Test
    public void simple() {
        Exception rt = new RuntimeException("Error");
        ProcessingException exp =
                new ProcessingException("Error",rt,
                                        new OptionsAndArgs(CommandDispatcher.getAvailableCommands()));
        checkException(exp,"command: list");
    }

    @Test
    public void quiet() {
        Exception rt = new RuntimeException("Error");
        ProcessingException exp =
                new ProcessingException("Error",rt,
                                        new OptionsAndArgs(CommandDispatcher.getAvailableCommands(),"start","12","--quiet"));
        checkException(exp,"");
    }

    @Test
    public void verbose() {
        Exception rt = new RuntimeException("Error");
        ProcessingException exp =
                new ProcessingException("Error",rt,
                                        new OptionsAndArgs(CommandDispatcher.getAvailableCommands(),"stop","14","--verbose"));
        checkException(exp,getClass().getName());
    }

    private void checkException(ProcessingException pExp,String pArg) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream old = System.err;
        try {
            System.setErr(new PrintStream(bos));
            pExp.printErrorMessage();
            if (pArg.length() > 0) {
                assertTrue(bos.toString().contains(pArg));
            } else {
                assertEquals(bos.toString().length(), 0);
            }
        } finally {
            System.setErr(old);
        }
    }
}
