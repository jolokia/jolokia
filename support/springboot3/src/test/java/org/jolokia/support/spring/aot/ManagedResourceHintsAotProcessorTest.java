package org.jolokia.support.spring.aot;

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

import java.lang.reflect.Method;
import java.util.Set;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedMetric;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class ManagedResourceHintsAotProcessorTest {

    @Test
    public void findsManagedMethodsOnManagedResourceBeans() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("managedBean", new RootBeanDefinition(ManagedBean.class));
        beanFactory.registerBeanDefinition("regularBean", new RootBeanDefinition(RegularBean.class));

        Set<Method> methods = ManagedResourceHintsAotProcessor.findManagedMethods(beanFactory);

        assertEquals(methods.size(), 3);
        assertTrue(containsMethod(methods, "operation"));
        assertTrue(containsMethod(methods, "attribute"));
        assertTrue(containsMethod(methods, "metric"));
    }

    @Test
    public void contributesOnlyWhenManagedMethodsArePresent() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("managedBean", new RootBeanDefinition(ManagedBean.class));

        assertNotNull(new ManagedResourceHintsAotProcessor().processAheadOfTime(beanFactory));
    }

    private boolean containsMethod(Set<Method> methods, String name) {
        return methods.stream().anyMatch(method -> method.getName().equals(name));
    }

    @ManagedResource
    static class ManagedBean {

        @ManagedOperation
        public void operation() {
        }

        @ManagedAttribute
        public String attribute() {
            return "value";
        }

        @ManagedMetric
        public int metric() {
            return 1;
        }

        public void notManaged() {
        }

    }

    static class RegularBean {

        @ManagedOperation
        public void ignoredOperation() {
        }

    }

}
