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
 * No-Op LogHandler
 * @author roland
 * @since 31/07/16
 */
public class QuietLogHandler implements LogHandler {
    public QuietLogHandler() {}
    public QuietLogHandler(String category) {}
    public void debug(String message) { }
    public void info(String message) { }
    public void error(String message, Throwable t) { }
    public boolean isDebug() { return false; }
}
