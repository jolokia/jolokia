package org.jolokia.server.core.backend;

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


/**
 * MBean for handling configuration issues from outside.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public interface ConfigMBean {

    // Name under which this bean gets registered
    String OBJECT_NAME = "jolokia:type=Config";

    /**
     * Get latest debug information if debugging is switched on. The returned output
     * will not take more than {@link #getMaxDebugEntries()} lines.
     *
     * @return debug info in plain ascii.
     */
    String debugInfo();

    /**
     * Reset all debug information stored internally
     */
    void resetDebugInfo();

    /**
     * Check, whether debugging is switched on
     * @return state of debugging
     */
    boolean isDebug();

    /**
     * Set debugging to given state
     * @param pSwitch true, if debugging should be switched on, false otherwise
     */
    void setDebug(boolean pSwitch);

    /**
     * Number of debug entries to remember
     *
     * @return number of debug entries
     */
    int getMaxDebugEntries();

    /**
     * Set the number of debugging info to remember
     * @param pNumber entries to set
     */
    void setMaxDebugEntries(int pNumber);
}
