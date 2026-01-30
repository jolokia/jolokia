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

import javax.management.openmbean.CompositeData;

import org.jolokia.client.jmxadapter.TypeHelperTest;

public class Address {
    private String city;
    private long zip;

    public Address() {
    }

    //        @ConstructorParameters({ "city", "zip" })
    public Address(String city, long zip) {
        this.city = city;
        this.zip = zip;
    }

    // used when there's no @ConstructorParameters-annotated constructor
    public static Address from(CompositeData cd) {
        Address a = new Address();
        a.city = (String) cd.get("city");
        a.zip = (long) cd.get("zip");
        return a;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public long getZip() {
        return zip;
    }

    public void setZip(long zip) {
        this.zip = zip;
    }
}
