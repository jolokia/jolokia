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

import java.util.concurrent.Callable;
import javax.security.auth.Subject;

/**
 * Interface for accessing {@link javax.security.auth.Subject} API for different JDKs (pre and post JEP-411).
 */
public interface SubjectAccess {

    /**
     * Wrapper for {@code Subject#doAs} (JDK 17) or {@code Subject#callAs} (JDK 18+)
     * @param subject
     * @param callable
     * @return
     * @param <T>
     * @throws Exception
     */
    <T> T callAs(Subject subject, Callable<T> callable) throws Exception;

}
