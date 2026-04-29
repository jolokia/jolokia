/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.server.core.util;

import org.jolokia.server.core.util.jaas.LegacySubjectAccess;
import org.jolokia.server.core.util.jaas.ModernSubjectAccess;

public class SubjectAccessProvider {

    /**
     * Obtain reflection-based accessor to {@link javax.security.auth.Subject} API which was changed in JEP-411
     * (removal of SecurityManager).
     * @return
     */
    public static SubjectAccess getSubjectAccess() {
        try {
            return new ModernSubjectAccess();
        } catch (UnsupportedOperationException e) {
            return new LegacySubjectAccess();
        }
    }

}
