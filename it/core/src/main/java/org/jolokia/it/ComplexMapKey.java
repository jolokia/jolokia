package org.jolokia.it;

import java.beans.ConstructorProperties;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * Test data for MXBeans
 * @author roland
 * @since 07.08.11
 */
public class ComplexMapKey implements Comparable<ComplexMapKey> {
    private int number;
    private String string;

    @ConstructorProperties({"string", "number"})
    public ComplexMapKey(String string, int number) {
        this.string = string;
        this.number = number;
    }

    public ComplexMapKey() {
        number = 1968;
        string = "late";
    }

    public int getNumber() {
        return number;
    }

    public String getString() {
        return string;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ComplexMapKey that = (ComplexMapKey) o;

        if (number != that.number) return false;
        if (string != null ? !string.equals(that.string) : that.string != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = number;
        result = 31 * result + (string != null ? string.hashCode() : 0);
        return result;
    }

    public int compareTo(ComplexMapKey o) {
        return this.number - o.number;
    }
}
