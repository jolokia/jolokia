package org.jolokia.mule;

import static org.testng.Assert.assertEquals;

import java.lang.reflect.Field;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/*
 * Copyright 2014 Michio Nakagawa
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
 * @author Michio Nakagawa
 * @since 10.10.14
 */
public class MuleAgentHttpServerFactoryTest {
    String originalValue = null;
    JolokiaMuleAgent agent = new JolokiaMuleAgent();

    @BeforeMethod
    public void setup() throws Exception {
        originalValue = MuleAgentHttpServerFactory.CLAZZ_NAME;
    }

    @AfterMethod
    public void teardown() throws Exception {
        setFieldValue(new MuleAgentHttpServerFactory(), "CLAZZ_NAME", originalValue);
    }

    @Test
    public void createServerOfMortbayPackage() throws Exception {
        MuleAgentHttpServer actual = MuleAgentHttpServerFactory.create(agent, agent);
        assertEquals(actual.getClass(), MortbayMuleAgentHttpServer.class);
    }

    @Test
    public void createServerOfEclipsePackage() throws Exception {
        setFieldValue(new MuleAgentHttpServerFactory(), "CLAZZ_NAME", "xxx");

        MuleAgentHttpServer actual = MuleAgentHttpServerFactory.create(agent, agent);
        assertEquals(actual.getClass(), EclipseMuleAgentHttpServer.class);
    }

    private void setFieldValue(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
