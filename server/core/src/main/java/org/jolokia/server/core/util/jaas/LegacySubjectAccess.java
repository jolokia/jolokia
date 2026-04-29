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
package org.jolokia.server.core.util.jaas;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;

import org.jolokia.server.core.util.SubjectAccess;

public class LegacySubjectAccess implements SubjectAccess {

    private final MethodHandle getContext;
    private final MethodHandle getSubject;
    private final MethodHandle doAs;

    public LegacySubjectAccess() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> controllerClass = Class.forName("java.security.AccessController");
            Class<?> contextClass = Class.forName("java.security.AccessControlContext");

            this.getContext = lookup.findStatic(controllerClass, "getContext", MethodType.methodType(contextClass));
            this.getSubject = lookup.findStatic(Subject.class, "getSubject", MethodType.methodType(Subject.class, contextClass));
            this.doAs = lookup.findStatic(Subject.class, "doAs", MethodType.methodType(Object.class, Subject.class, PrivilegedExceptionAction.class));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | UnsupportedOperationException e) {
            throw new UnsupportedOperationException("Use JDK18+ (JEP 411) javax.security.auth.Subject API");
        }
        try {
            getSubject.invoke(getContext.invoke());
        } catch (Throwable e) {
            throw new UnsupportedOperationException("Use JDK18+ (JEP 411) javax.security.auth.Subject API");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T callAs(Subject subject, Callable<T> callable) throws Exception {
        try {
            return (T) this.doAs.invoke(subject, (PrivilegedExceptionAction<T>) callable::call);
        } catch (Exception e) {
            throw e;
        } catch (Throwable e) {
            throw new UnsupportedOperationException("Can't use legacy javax.security.auth.Subject.doAs() call", e);
        }
    }

}
