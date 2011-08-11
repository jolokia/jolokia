package org.jolokia.it;

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

import java.util.HashMap;
import java.util.Map;

/**
 * @author roland
 * @since 07.08.11
 */
public class MxBeanSample implements MxBeanSampleMXBean {

    int[] numbers = new int[] { 47, 11} ;
    private ComplexTestData complex;
    private Map<String, Long> map;

    public MxBeanSample() {
        map = new HashMap<String, Long>();
        map.put("magic",42L);
    }

    public int[] getNumbers() {
        return numbers;
    }

    public void setNumbers(int[] pNumbers) {
        numbers = pNumbers;
    }

    public ComplexTestData getComplexTestData() {
        return complex;
    }

    public void setComplextTestData(ComplexTestData testData) {
        complex = testData;
    }

    public Map<String, Long> getMap() {
        return map;
    }

    public void setMap(Map<String, Long> pMap) {
        map = pMap;
    }

    public int exec(long arg) {
        return 0;
    }

    public int exec(ComplexTestData arg) {
        return 1;
    }
}
