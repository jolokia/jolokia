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

import java.util.Map;

/**
 * Test bean for MXBean
 *
 * @author roland
 * @since 07.08.11
 */
public interface MxBeanSampleMXBean {

    int[] getNumbers();
    void setNumbers(int[] pNumbers);

    ComplexTestData getComplexTestData();
    void setComplexTestData(ComplexTestData testData);

    Map<ComplexMapKey,String> getMapWithComplexKey();
    void setMapWithComplexKey(Map<ComplexMapKey,String> pMap);

    Map<String,Long> getMap();
    void setMap(Map<String,Long> pMap);

    int exec(long arg);
    int exec(ComplexTestData arg);

    String echo(String message);
    PojoBean getPojoBean();
    void setPojoBean(PojoBean bean);
    PojoBean echoBean(PojoBean bean);
}
