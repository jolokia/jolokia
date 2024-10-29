/*
 * Copyright 2009-2024 Roland Huss
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
package org.jolokia.server.core.auth;

import java.security.Principal;
import java.util.Collections;
import javax.security.auth.Subject;

public final class JolokiaAgentPrincipal implements Principal {

    public static final Principal INSTANCE = new JolokiaAgentPrincipal();
    private static final Subject SUBJECT = new Subject(true, Collections.singleton(INSTANCE), Collections.emptySet(), Collections.emptySet());

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    public static Subject asSubject() {
        return SUBJECT;
    }

}
