package org.jolokia.config;

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

import java.util.Enumeration;

/**
 * Interface used for extracting configuration from various backend
 * configuration like SevletContext or servlet config
 * @author roland
 * @since 07.02.13
 */
public interface ConfigExtractor {
    /**
     * Get all configuration name
     * @return enumeration of config names
     */
    Enumeration getNames();

    /**
     * Get the parameter for a certain
     * @param pKeyS string representation of the config key to fetch
     * @return the value of the configuration parameter or <code>null</code> if no such parameter exists
     */
    String getParameter(String pKeyS);
}
