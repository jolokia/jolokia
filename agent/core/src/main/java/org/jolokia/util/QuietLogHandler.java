package org.jolokia.util;
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

/**
 * Loghandler which doesn not output anything
 */
public class QuietLogHandler implements LogHandler {
       /** {@inheritDoc} */
    public void debug(String message) { }

    /** {@inheritDoc} */
    public void info(String message) { }

    /** {@inheritDoc} */
    public void error(String message, Throwable t) { }
}
