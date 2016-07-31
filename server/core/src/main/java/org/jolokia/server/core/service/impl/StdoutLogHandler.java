package org.jolokia.server.core.service.impl;
/*
 *
 * Copyright 2016 Roland Huss
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

import org.jolokia.server.core.service.api.LogHandler;

/**
 * Loghandler for printing to stdout
 */
public class StdoutLogHandler implements LogHandler {
    private boolean doDebug;

    public StdoutLogHandler(boolean pDoDebug) {
        doDebug = pDoDebug;
    }

    public StdoutLogHandler() {
        this(false);
    }

    public StdoutLogHandler(String category) {
        this(false);
    }

    public void debug(String message) {
        if (doDebug) {
            log("D> " + message);
        }
    }

    public void info(String message) {
        log("I> " + message);
    }

    public void error(String message, Throwable t) {
        log("E> " + message);
        if (t != null) {
            t.printStackTrace();
        }
    }

    public boolean isDebug() {
        return doDebug;
    }

    private void log(String message) {
        System.out.println(message); //NOSONAR
    }
}
