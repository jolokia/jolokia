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
package org.jolokia.server.core.service.impl;

import java.util.SortedSet;

import org.jolokia.server.core.detector.ServerDetector;
import org.jolokia.server.core.detector.ServerDetectorLookup;
import org.jolokia.server.core.service.api.LogHandler;

public class CachingServerDetectorLookup implements ServerDetectorLookup {

    private final ServerDetectorLookup delegate;
    private SortedSet<ServerDetector> serverDetectors = null;

    public CachingServerDetectorLookup(ServerDetectorLookup delegate) {
        this.delegate = delegate;
    }

    @Override
    public SortedSet<ServerDetector> lookup(LogHandler logHandler) {
        if (this.serverDetectors == null) {
            this.serverDetectors = delegate.lookup(logHandler);
        }

        return this.serverDetectors;
    }

}
