package org.jolokia.handler;

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

import java.util.*;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.jolokia.backend.executor.AbstractMBeanServerExecutor;
import org.jolokia.backend.executor.MBeanServerExecutor;
import org.json.simple.JSONObject;

import static org.testng.AssertJUnit.assertEquals;

/**
 * @author roland
 * @since 17.01.13
 */
public class BaseHandlerTest {

    protected MBeanServerExecutor getMBeanServerManager(final MBeanServerConnection... connections) {
        return new AbstractMBeanServerExecutor() {

            protected Set<MBeanServerConnection> getMBeanServers() {
                return new HashSet<MBeanServerConnection>(Arrays.asList(connections));
            }
        };
    }

    protected void verifyTagFormatValue(Map res, ObjectName oName, Object expectedValue, String ... extraValues) {
        assertEquals(oName.getKeyPropertyList().size() + 2 + (extraValues.length / 2), res.size());
        assertEquals(oName.getDomain(), res.get(ValueFormat.KEY_DOMAIN));
        Map<String,String> props = oName.getKeyPropertyList();
        for (Map.Entry<String,String> entry : props.entrySet()) {
            assertEquals(entry.getValue(),res.get(entry.getKey()));
        }
        for (int i = 0; i < extraValues.length; i+=2) {
            assertEquals(extraValues[i+1],res.get(extraValues[i]));
        }
        assertEquals(expectedValue, res.get(ValueFormat.KEY_VALUE));
    }
}
