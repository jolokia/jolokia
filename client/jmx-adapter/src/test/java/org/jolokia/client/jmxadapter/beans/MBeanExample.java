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
package org.jolokia.client.jmxadapter.beans;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MBeanExample implements MBeanExampleMBean {

    @Override
    public void unsupportedOperation() {
        throw new UnsupportedOperationException("ha ha");
    }

    @Override
    public void unexpectedFailureMethod() {
        throw new NullPointerException("uh oh");
    }

    @Override
    public List<String> getEmptyList() {
        return Collections.emptyList();
    }

    @Override
    public Set<String> getSetAttribute() {
        return new HashSet<>(Arrays.asList("foo", "bar"));
    }

    @Override
    public Set<String> doEmptySetOperation() {
        return Collections.emptySet();
    }

    @Override
    public Map<String, String> getMapAttribute() {
        return Collections.singletonMap("foo", "bar");
    }

    @Override
    public Map<String, String> doMapOperation() {
        return getMapAttribute();
    }

}
