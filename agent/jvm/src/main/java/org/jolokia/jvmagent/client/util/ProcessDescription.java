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

/**
 * Process descriptor, immutable
 */
public class ProcessDescription {
    private String id;
    private String display;

    /**
     * Constructor for process descriptor
     * @param pId procuess id
     * @param pDisplay process description
     */
    public ProcessDescription(String pId, String pDisplay) {
        id = pId;
        display = pDisplay;
    }

    /**
     * Process id
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * Process description
     * @return description
     */
    public String getDisplay() {
        return display;
    }
}
