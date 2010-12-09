/*
 * Copyright 2009-2010 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.util;

import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 02.12.10
 */
public class ServiceObjectFactoryTest {

    @Test
    public void testOrder() {
        List<TestService> service =
                ServiceObjectFactory.createServiceObjects("service/test-services-default", "service/test-services");
        String[] orderExpected = new String[] { "three", "two", "five", "one" };
        assertEquals(service.size(),4);
        Iterator<TestService> it = service.iterator();
        for (String val : orderExpected) {
            assertEquals(it.next().getName(),val);
        }
    }

    interface TestService { String getName(); };
    public static class Test1 implements TestService { public String getName() { return "one"; } };
    public static class Test2 implements TestService { public String getName() { return "two"; } };
    public static class Test3 implements TestService { public String getName() { return "three"; } };
    public static class Test4 implements TestService { public String getName() { return "four"; } };
    public static class Test5 implements TestService { public String getName() { return "five"; } };
}
