/*
 * Copyright 2009-2024 Roland Huss
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
package org.jolokia.server.core.util.jmx;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.Set;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.MXBean;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.StandardMBean;
import javax.management.modelmbean.DescriptorSupport;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanConstructorInfo;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.ModelMBeanInfoSupport;
import javax.management.modelmbean.ModelMBeanNotificationInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;
import javax.management.modelmbean.RequiredModelMBean;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanAttributeInfoSupport;
import javax.management.openmbean.OpenMBeanConstructorInfo;
import javax.management.openmbean.OpenMBeanInfo;
import javax.management.openmbean.OpenMBeanInfoSupport;
import javax.management.openmbean.OpenMBeanOperationInfo;
import javax.management.openmbean.SimpleType;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests for JMX API itself
 */
public class JmxTest {

    @Test
    public void fourMBeanTypes() throws MalformedObjectNameException, InstanceAlreadyExistsException, MBeanException, NotCompliantMBeanException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException, InvalidAttributeValueException, IntrospectionException, InvalidTargetObjectTypeException, NoSuchMethodException {
        // 1.4.1.1 Managed Beans (MBeans)
        //  - Standard MBeans are the simplest to design and implement, their management
        //    interface is described by their method names. MXBeans are a kind of Standard
        //    MBean that uses concepts from Open MBeans to allow universal manageability
        //    while simplifying coding.
        //  - Dynamic MBeans must implement a specific interface, but they expose their
        //    management interface at runtime for greatest flexibility.
        //  - Open MBeans are dynamic MBeans that rely on basic data types for universal
        //    manageability and that are self describing for user-friendliness.
        //  - Model MBeans are also dynamic MBeans that are fully configurable and self
        //    described at runtime; they provide a generic MBean class with default behavior
        //    for dynamic instrumentation of resources.
        //
        // 2.1 Definition
        // An MBean is a concrete Java class that includes the following instrumentation:
        //  - The implementation of its own corresponding MBean interface
        //    or an implementation of the DynamicMBean interface
        //  - Optionally, an implementation of the NotificationBroadcaster interface

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        Set<ObjectName> names = server.queryNames(null, null);
        for (ObjectName name : names) {
            System.out.printf(" - %s: %s%n", name, server.getMBeanInfo(name).getClassName());
        }

        try {
            server.registerMBean(new JustAClass("Hello"), ObjectName.getInstance("jolokia:type=Bad"));
            fail("Should have thrown an NotCompliantMBeanException");
        } catch (NotCompliantMBeanException ignored) {
        }

        // 1.1 standard MBeans
        ObjectName standardMBeanName = ObjectName.getInstance("jolokia:type=StandardMBean");
        server.registerMBean(new MyResource(), standardMBeanName);
        Object info = server.getAttribute(standardMBeanName, "Info");
        assertEquals(info, "Hello");
        MBeanInfo standardMBeanInfo = server.getMBeanInfo(standardMBeanName);
        assertEquals(standardMBeanInfo.getAttributes().length, 1);

        // 1.2 MXBeans
        ObjectName standardMXBeanName = ObjectName.getInstance("jolokia:type=StandardXMBean");
        server.registerMBean(new MyXResource(), standardMXBeanName);
        info = server.getAttribute(standardMXBeanName, "Info");
        assertEquals(info, "Hello");
        MBeanInfo standardMXBeanInfo = server.getMBeanInfo(standardMXBeanName);
        assertEquals(standardMXBeanInfo.getAttributes().length, 1);

        // 2 DynamicMBean Interface
        ObjectName dynamicMBeanName = ObjectName.getInstance("jolokia:type=DynamicMBean");
        server.registerMBean(new MyDynamicResource(), dynamicMBeanName);
        server.setAttribute(dynamicMBeanName, new Attribute("Info", "Welcome!"));
        info = server.getAttribute(dynamicMBeanName, "Info");
        assertEquals(info, "Welcome!");
        MBeanInfo dynamicMBeanInfo = server.getMBeanInfo(dynamicMBeanName);
        assertEquals(dynamicMBeanInfo.getAttributes().length, 1);
        assertFalse(dynamicMBeanInfo instanceof OpenMBeanInfo);

        // 2.1 DynamicMBean that helps dealing with StandardMBeans
        // Chapter "2.7.1 MBeanInfo Class" says:
        //     The introspection of standard MBeans provides a simple generic
        //     description string for the MBeanInfo object and all its components. Therefore, all
        //     standard MBeans will have the same description. The StandardMBean class
        //     provides a way to add custom descriptions while keeping the convenience of the
        //     standard MBean design patterns.
        //
        // but it doesn't say much about how to do it
        ObjectName dynamicStandardMBeanName = ObjectName.getInstance("jolokia:type=DynamicStandardMBean");
        StandardMBean dynamicStandardMBean = new StandardMBean(new MyPlainResource(), IMyPlainResource.class, false) {
            @Override
            protected String getDescription(MBeanInfo info) {
                return "Custom description";
            }
        };
        server.registerMBean(dynamicStandardMBean, dynamicStandardMBeanName);
        info = server.getAttribute(dynamicStandardMBeanName, "Info");
        assertEquals(info, "Hello");
        // non-open type
        assertTrue(server.getAttribute(dynamicStandardMBeanName, "Now") instanceof Temporal);
        MBeanInfo dynamicStandardMBeanInfo = server.getMBeanInfo(dynamicStandardMBeanName);
        assertEquals(dynamicStandardMBeanInfo.getAttributes().length, 2);
        assertEquals(dynamicStandardMBeanInfo.getDescription(), "Custom description");

        // 3 Open MBeans

        // Chapter "3.1 Overview"
        //     To provide its own description to management applications, an open MBean must be
        //     a dynamic MBean (see “Dynamic MBeans” on page 46). Beyond the DynamicMBean
        //     interface, there is no corresponding “open” interface that must be implemented.
        //     Instead, an MBean earns its “openness” by providing a descriptively rich metadata
        //     and by using only certain predefined data types in its management interface.
        //
        //     An MBean indicates whether it is open or not through the MBeanInfo object it
        //     returns. Open MBeans return an OpenMBeanInfo object, a subclass of MBeanInfo.
        ObjectName openMBeanName = ObjectName.getInstance("jolokia:type=OpenMBean");
        server.registerMBean(new MyOpenResource(), openMBeanName);
        server.setAttribute(openMBeanName, new Attribute("Info", "Welcome!"));
        info = server.getAttribute(openMBeanName, "Info");
        assertEquals(info, "Welcome!");
        MBeanInfo openMBeanInfo = server.getMBeanInfo(openMBeanName);
        assertEquals(openMBeanInfo.getAttributes().length, 1);
        assertTrue(openMBeanInfo instanceof OpenMBeanInfo);

        // 4 Model MBeans
        //     A model MBean is a generic, configurable MBean that anyone can use to instrument
        //     almost any resource rapidly. Model MBeans are dynamic MBeans that also
        //     implement the interfaces specified in this chapter. These interfaces define structures
        //     that, when implemented, provide an instantiable MBean with default and
        //     configurable behavior.
        //
        //     Any implementation of the model MBean must implement the ModelMBean
        //     interface that extends the DynamicMBean, PersistentMBean and
        //     ModelMBeanNotificationBroadcaster interfaces. The model MBean must
        //     expose its metadata in a ModelMBeanInfoSupport object that extends MBeanInfo
        //     and implements the ModelMBeanInfo interface.
        //
        // Like all other MBeans, the metadata of a model MBean contains the list of attributes,
        // operations, constructors, and notifications of the management interface. Model MBeans also
        // describe their target object and their policies for accessing the target object. This
        // information is contained in an object called a descriptor, defined by the Descriptor
        // interface and implemented in the DescriptorSupport class.
        //
        // Spring does a lot of work in
        // org.springframework.jmx.export.assembler.AbstractMBeanInfoAssembler.getMBeanInfo() to prepare
        // ModelMBeanInfo
        ObjectName modelMBeanName = ObjectName.getInstance("jolokia:type=ModelMBean");
        RequiredModelMBean rmmb = new RequiredModelMBean();
        rmmb.setManagedResource(new MyResource(), "ObjectReference");
        rmmb.setModelMBeanInfo(new ModelMBeanInfoSupport(
            MyResource.class.getName(), "Description",
            new ModelMBeanAttributeInfo[] {
                new ModelMBeanAttributeInfo("Info", "Information", MyResource.class.getMethod("getInfo"), null, new DescriptorSupport(
                    new String[] { "name", "descriptorType", "getMethod" },
                    new String[] { "Info", "attribute", "getInfo" }
                ))
            },
            new ModelMBeanConstructorInfo[0],
            new ModelMBeanOperationInfo[] {
                new ModelMBeanOperationInfo("getInfo", MyResource.class.getMethod("getInfo"))
            },
            new ModelMBeanNotificationInfo[0]
        ));
        server.registerMBean(rmmb, modelMBeanName);
        MBeanInfo modelMBeanInfo = server.getMBeanInfo(modelMBeanName);
        assertEquals(modelMBeanInfo.getAttributes().length, 1);
        assertTrue(modelMBeanInfo instanceof ModelMBeanInfo);
        info = server.getAttribute(modelMBeanName, "Info");
        assertEquals(info, "Hello");

        server.unregisterMBean(modelMBeanName);
        server.unregisterMBean(openMBeanName);
        server.unregisterMBean(dynamicStandardMBeanName);
        server.unregisterMBean(dynamicMBeanName);
        server.unregisterMBean(standardMXBeanName);
        server.unregisterMBean(standardMBeanName);
    }

    public static class JustAClass {
        private final String info;

        public JustAClass(String info) {
            this.info = info;
        }

        public String getInfo() {
            return info;
        }
    }

    public interface MyResourceMBean {
        String getInfo();
    }

    public static class MyResource implements MyResourceMBean {

        @Override
        public String getInfo() {
            return "Hello";
        }
    }

    @MXBean
    public interface IMyResource {
        String getInfo();
    }

    public static class MyXResource implements IMyResource {

        @Override
        public String getInfo() {
            return "Hello";
        }
    }

    public interface IMyPlainResource {
        Instant getNow();
        String getInfo();
    }

    public static class MyPlainResource implements IMyPlainResource {

        @Override
        public Instant getNow() {
            return Instant.now();
        }

        @Override
        public String getInfo() {
            return "Hello";
        }
    }

    public static class MyDynamicResource implements DynamicMBean {

        private String info;

        @Override
        public MBeanInfo getMBeanInfo() {
            String className = this.getClass().getName();
            String description = "My Dynamic Resource";
            MBeanAttributeInfo[] attributes = new MBeanAttributeInfo[]{
                new MBeanAttributeInfo("Info", String.class.getName(), "Information", true, true, false)
            };
            MBeanConstructorInfo[] constructors = new MBeanConstructorInfo[] {};
            MBeanOperationInfo[] operations = new MBeanOperationInfo[] {};
            MBeanNotificationInfo[] notifications = new MBeanNotificationInfo[] {};
            return new MBeanInfo(
                className, description,
                attributes, constructors, operations, notifications
            );
        }

        @Override
        public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
            return null;
        }

        @Override
        public AttributeList setAttributes(AttributeList attributes) {
            return null;
        }

        @Override
        public AttributeList getAttributes(String[] attributes) {
            return null;
        }

        @Override
        public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException {
            if ("Info".equals(attribute.getName())) {
                if (attribute.getValue() instanceof String) {
                    info = (String) attribute.getValue();
                    return;
                }
                throw new InvalidAttributeValueException("Value " + attribute.getValue() + " is not a String");
            }
            throw new AttributeNotFoundException("Attribute " + attribute.getName() + " not found");
        }

        @Override
        public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
            if ("Info".equals(attribute)) {
                return info;
            }
            throw new AttributeNotFoundException(attribute);
        }
    }

    public static class MyOpenResource implements DynamicMBean {

        private String info;

        @Override
        public MBeanInfo getMBeanInfo() {
            String className = this.getClass().getName();
            String description = "My Dynamic Resource";
            OpenMBeanAttributeInfo[] attributes = new OpenMBeanAttributeInfo[]{
                new OpenMBeanAttributeInfoSupport("Info", "Information", SimpleType.STRING, true, true, false)
            };
            OpenMBeanConstructorInfo[] constructors = new OpenMBeanConstructorInfo[] {};
            OpenMBeanOperationInfo[] operations = new OpenMBeanOperationInfo[] {};
            MBeanNotificationInfo[] notifications = new MBeanNotificationInfo[] {};
            return new OpenMBeanInfoSupport(
                className, description,
                attributes, constructors, operations, notifications
            );
        }

        @Override
        public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
            return null;
        }

        @Override
        public AttributeList setAttributes(AttributeList attributes) {
            return null;
        }

        @Override
        public AttributeList getAttributes(String[] attributes) {
            return null;
        }

        @Override
        public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException {
            if ("Info".equals(attribute.getName())) {
                if (attribute.getValue() instanceof String) {
                    info = (String) attribute.getValue();
                    return;
                }
                throw new InvalidAttributeValueException("Value " + attribute.getValue() + " is not a String");
            }
            throw new AttributeNotFoundException("Attribute " + attribute.getName() + " not found");
        }

        @Override
        public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
            if ("Info".equals(attribute)) {
                return info;
            }
            throw new AttributeNotFoundException(attribute);
        }
    }

}
