package org.jolokia.client.request;

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

import javax.management.MalformedObjectNameException;

import org.jolokia.client.exception.J4pException;
import org.jolokia.it.ItSetup;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 13.01.13
 */
public class JSR160HiddenTest extends AbstractJ4pIntegrationTest {

    @Test
    public void hiddenMBeanNotAvailableForJSR160() throws MalformedObjectNameException, J4pException {
        try {
            J4pReadRequest request = new J4pReadRequest(getTargetProxyConfig(),
                                                        ItSetup.JOLOKIA_IT_DOMAIN_HIDDEN + ":type=attribute","LongSeconds");
            j4pClient.execute(request);
            fail("Exception should have been thrown");
        } catch (J4pException exp) {
            assertTrue(exp.getMessage().contains("InstanceNotFoundException"));
        }

    }

    @Test
    public void hiddenMBeanAvailableForJolokia() throws MalformedObjectNameException, J4pException {
        J4pReadRequest request = new J4pReadRequest(ItSetup.JOLOKIA_IT_DOMAIN + ":type=attribute","LongSeconds");
        J4pReadResponse response = j4pClient.execute(request);
        assertNotNull(response.getValue());
    }
}
