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

import java.math.BigInteger;
import java.util.Map;
import javax.management.ObjectName;

public class Settable1 implements Settable1MXBean {

    private String stringValue;
    public BigInteger bigIntValue;
    private Long longValue;

    private Map<String, ObjectName> mapping;

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }

    public void setBigIntValue(BigInteger bigIntValue) {
        this.bigIntValue = bigIntValue;
    }

    public Long getLongValue() {
        return longValue;
    }

    public Map<String, ObjectName> getMapping() {
        return mapping;
    }

    public void setMapping(Map<String, ObjectName> mapping) {
        this.mapping = mapping;
    }

}
