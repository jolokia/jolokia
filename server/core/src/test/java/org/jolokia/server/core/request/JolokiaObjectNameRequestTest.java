/*
 * Copyright 2009-2013 Roland Huss
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

package org.jolokia.server.core.request;

import javax.management.MalformedObjectNameException;

import org.jolokia.server.core.util.RequestType;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 21.11.13
 */

public class JolokiaObjectNameRequestTest {

    @Test
    public void realms() throws MalformedObjectNameException {

        String[] testData = new String[] {
                "spring@:name=propertyBean","spring","","name=propertyBean",
                "zk@domain:bla=blu,alfa=beta","zk","domain","bla=blu,alfa=beta",
                "java.lang:type=Memory",null,"java.lang","type=Memory"
        };
        for (int i = 0; i < testData.length; i+= 4) {
            JolokiaReadRequest request =
                    new JolokiaRequestBuilder(RequestType.READ,testData[i]).build();
            assertEquals(request.getRealm(), testData[i+1]);
            assertEquals(request.getObjectName().getDomain(),testData[i+2]);
            assertEquals(request.getObjectName().getKeyPropertyListString(),testData[i+3]);
            if (testData[i+1] != null) {
                assertTrue(request.toString().contains(testData[i+1]));
            }
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = "^.*null.*$")
    public void illegalObjectName() throws MalformedObjectNameException {
            JolokiaReadRequest request =
                    new JolokiaRequestBuilder(RequestType.READ, (String) null).build();
    }
}
