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

package org.jolokia.util;

/**
 * Very simple log handler printing out logging to standard out
 *
 * @author roland
 * @since 19.04.13
 */
public class StdoutLogHandler implements LogHandler {

    private boolean debug = true;

    public StdoutLogHandler(boolean pDebug) {
        debug = pDebug;
    }

    public StdoutLogHandler() {
        this(true);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public void debug(String message) {
        if (isDebug()) {
            System.out.println("[DEBUG] " + message);
        }
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public void info(String message) {
        System.out.println("[INFO] " + message);
    }

    @SuppressWarnings({"PMD.SystemPrintln","PMD.AvoidPrintStackTrace"})
    public void error(String message, Throwable t) {
        System.out.println("[ERROR] " + message);
        t.printStackTrace();
    }

    public boolean isDebug() {
        return debug;
    }
}
