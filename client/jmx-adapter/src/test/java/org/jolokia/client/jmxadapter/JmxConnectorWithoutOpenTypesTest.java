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
package org.jolokia.client.jmxadapter;

import java.util.Map;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.NotCompliantMBeanException;

import org.jolokia.client.JolokiaClientOption;
import org.testng.annotations.Test;

/**
 * Tests for connections to Jolokia Agent which didn't provide {@link javax.management.openmbean.OpenType}
 * information - very helpful when reconstructing the values.
 */
public class JmxConnectorWithoutOpenTypesTest extends JmxConnectorTest {

    @Override
    protected Map<String, Object> env() {
        return Map.of(JolokiaClientOption.OPEN_TYPES.asSystemProperty(), "false");
    }

    @Override
    protected boolean useOpenTypeInformation() {
        return false;
    }

    // These overrides look silly, but I added them here to make it easier to run the tests from IDE

    @Test
    @Override
    public void createConnectorUsingURI() throws Exception {
        super.createConnectorUsingURI();
    }

    @Test
    @Override
    public void getMBeanInfoForIntrospection() throws Exception {
        super.getMBeanInfoForIntrospection();
    }

    @Test
    @Override
    public void getMixedInfoForIntrospection() throws Exception {
        super.getMixedInfoForIntrospection();
    }

    @Test
    @Override
    public void getJFRInfoForIntrospection() throws Exception {
        super.getJFRInfoForIntrospection();
    }

    @Test
    @Override
    public void illegalMXBeans() throws Exception {
        super.illegalMXBeans();
    }

    @Test
    @Override
    public void customComposites() throws Exception {
        super.customComposites();
    }

    @Test(expectedExceptions = NotCompliantMBeanException.class)
    @Override
    public void customRecursiveComposites() throws Exception {
        super.customRecursiveComposites();
    }

    @Test
    @Override
    public void dynamicTabularType() throws Exception {
        super.dynamicTabularType();
    }

    @Test
    @Override
    public void getMBeanInfoForKnownMBean() throws Exception {
        super.getMBeanInfoForKnownMBean();
    }

    @Test
    @Override
    public void getStringAttributeFromKnownMBean() throws Exception {
        super.getStringAttributeFromKnownMBean();
    }

    @Test(expectedExceptions = AttributeNotFoundException.class, expectedExceptionsMessageRegExp = "No such attribute: Jolokia")
    @Override
    public void getUnknownAttributeFromKnownMBean() throws Exception {
        super.getUnknownAttributeFromKnownMBean();
    }

    @Test
    @Override
    public void getKnownAndUnknownAttributeFromKnownMBean() throws Exception {
        super.getKnownAndUnknownAttributeFromKnownMBean();
    }

    @Test(expectedExceptions = InstanceNotFoundException.class)
    @Override
    public void getAttributeForPatternMBeanFromPlatformServer() throws Exception {
        super.getAttributeForPatternMBeanFromPlatformServer();
    }

    @Test
    @Override
    public void getBooleanAttributeFromKnownMBean() throws Exception {
        super.getBooleanAttributeFromKnownMBean();
    }

    @Test
    @Override
    public void getNumericAttributeFromKnownMBean() throws Exception {
        super.getNumericAttributeFromKnownMBean();
    }

    @Test
    @Override
    public void getStringArrayAttributeFromKnownMBean() throws Exception {
        super.getStringArrayAttributeFromKnownMBean();
    }

    @Test
    @Override
    public void firstGetAttributesCallFromJConsole() throws Exception {
        super.firstGetAttributesCallFromJConsole();
    }

    @Test
    @Override
    public void setSingleMXBeanRWAttribute() throws Exception {
        super.setSingleMXBeanRWAttribute();
    }

    @Test
    @Override
    public void setSingleMXBeanWriteOnlyAttribute() throws Exception {
        super.setSingleMXBeanWriteOnlyAttribute();
    }

    @Test
    @Override
    public void setTabularAttribute() throws Exception {
        super.setTabularAttribute();
    }

    @Test
    @Override
    public void setTabularAttributeViaMXBeanProxy() throws Exception {
        super.setTabularAttributeViaMXBeanProxy();
    }

    @Test
    @Override
    public void setUnknownMXBeanAttribute() throws Exception {
        super.setUnknownMXBeanAttribute();
    }

    @Test
    @Override
    public void setMultipleMXBeanAttributes() throws Exception {
        super.setMultipleMXBeanAttributes();
    }

    @Test
    @Override
    public void setProperAndUnknownMXBeanAttributes() throws Exception {
        super.setProperAndUnknownMXBeanAttributes();
    }

    @Test
    @Override
    public void invokeKnownMXBean() throws Exception {
        super.invokeKnownMXBean();
    }

}
