package org.jolokia.jvmagent.client;

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

import java.security.Permission;

import org.testng.annotations.Test;

/**
 * @author roland
 * @since 28.09.11
 */
@Test(groups = "java6")
public class AgentLauncherTest {


    
    @Test(enabled = false)
    public void simple() {
        forbidSystemExitCall();
        try {
            AgentLauncher.main();
        } catch (ExitTrappedException exp) {

        } finally {
            enableSystemExitCall();
        }
    }


    private static void forbidSystemExitCall() {
        final SecurityManager securityManager = new SecurityManager() {
            public void checkPermission(Permission permission) {
                if("exitVM".equals( permission.getName())) {
                    throw new ExitTrappedException();
                }
            }
        };
        System.setSecurityManager(securityManager);
    }

    private static void enableSystemExitCall() {
        System.setSecurityManager(null);
    }

    private static class ExitTrappedException extends RuntimeException { }

}
