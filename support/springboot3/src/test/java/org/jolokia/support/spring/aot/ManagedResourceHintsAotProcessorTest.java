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
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.aot.generate.GeneratedClasses;
import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedMetric;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.jmx.export.metadata.JmxAttributeSource;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ManagedResourceHintsAotProcessorTest {

    @Test
    public void findsManagedMethodsOnManagedResourceBeans() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("managedBean", new RootBeanDefinition(ManagedBean.class));
        beanFactory.registerBeanDefinition("regularBean", new RootBeanDefinition(RegularBean.class));

        Set<Method> methods = ManagedResourceHintsAotProcessor.findManagedMethods(beanFactory);

        assertEquals(methodNames(methods), Set.of("operation", "attribute", "metric"));
    }

    @Test
    public void contributesForManagedResourceBeans() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("managedBean", new RootBeanDefinition(ManagedBean.class));

        assertNotNull(new ManagedResourceHintsAotProcessor().processAheadOfTime(beanFactory));
    }

    @Test
    public void skipsContributionWithoutManagedResourceBeans() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("regularBean", new RootBeanDefinition(RegularBean.class));

        assertNull(new ManagedResourceHintsAotProcessor().processAheadOfTime(beanFactory));
    }

    @Test
    public void registersInvokeHintsForManagedMethods() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("managedBean", new RootBeanDefinition(ManagedBean.class));

        RuntimeHints hints = applyContribution(beanFactory);

        TypeHint typeHint = hints.reflection().getTypeHint(ManagedBean.class);
        assertNotNull(typeHint, "missing reflection hint for the @ManagedResource bean");
        assertEquals(typeHint.methods()
            .map(hint -> hint.getName())
            .collect(Collectors.toSet()), Set.of("operation", "attribute", "metric"));
        assertTrue(typeHint.methods().allMatch(hint -> hint.getMode() == ExecutableMode.INVOKE),
            "managed methods must be invokable, not just introspectable");
    }

    /**
     * {@code MBeanExporter.autodetect()} reads bean definitions and manually registered
     * singletons, so a {@code registerSingleton()} resource needs the same hints.
     */
    @Test
    public void registersHintsForManuallyRegisteredSingletons() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("managedSingleton", new ManagedBean());

        RuntimeHints hints = applyContribution(beanFactory);

        assertMetadataHints(hints);
        TypeHint typeHint = hints.reflection().getTypeHint(ManagedBean.class);
        assertNotNull(typeHint, "missing reflection hint for the singleton @ManagedResource bean");
        assertEquals(methodNames(ManagedResourceHintsAotProcessor.findManagedMethods(beanFactory)),
            Set.of("operation", "attribute", "metric"));
    }

    @Test
    public void registersMetadataHintsForManagedResourceBeans() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("managedBean", new RootBeanDefinition(ManagedBean.class));

        assertMetadataHints(applyContribution(beanFactory));
    }

    /**
     * A {@link ManagedResource} without any managed method is still exported, and Spring reads
     * its resource metadata reflectively while doing so.
     */
    @Test
    public void registersMetadataHintsForManagedResourceBeansWithoutManagedMethods() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("bareBean", new RootBeanDefinition(BareManagedBean.class));

        assertMetadataHints(applyContribution(beanFactory));
    }

    /**
     * Guards the property from the reported native image failure: {@code MBeanExporter} writes
     * {@code currencyTimeLimit} on the metadata bean at startup, and the setter is declared on a
     * superclass. Looking the declaring class up here keeps the test honest if Spring moves it.
     */
    @Test
    public void registersHintsForTheClassDeclaringCurrencyTimeLimit() throws NoSuchMethodException {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("managedBean", new RootBeanDefinition(ManagedBean.class));

        RuntimeHints hints = applyContribution(beanFactory);

        Class<?> declaringClass = org.springframework.jmx.export.metadata.ManagedResource.class
            .getMethod("setCurrencyTimeLimit", int.class)
            .getDeclaringClass();
        TypeHint typeHint = hints.reflection().getTypeHint(declaringClass);
        assertNotNull(typeHint, "missing reflection hint for " + declaringClass.getName()
            + ", which declares setCurrencyTimeLimit");
        assertTrue(typeHint.getMemberCategories().contains(MemberCategory.INVOKE_DECLARED_METHODS),
            "setCurrencyTimeLimit is not invokable on " + declaringClass.getName());
    }

    /**
     * Asserts that every JMX metadata class Spring fills reflectively is covered. The expected
     * set is derived from {@link JmxAttributeSource} instead of being copied from the processor,
     * so a new metadata type in Spring fails this test rather than passing silently.
     */
    private void assertMetadataHints(RuntimeHints hints) {
        Set<Class<?>> expectedTypes = jmxMetadataTypes();
        assertTrue(expectedTypes.size() >= 7, "unexpectedly few metadata types: " + expectedTypes);
        for (Class<?> metadataType : expectedTypes) {
            TypeHint typeHint = hints.reflection().getTypeHint(metadataType);
            assertNotNull(typeHint, "missing reflection hint for " + metadataType.getName());
            Set<MemberCategory> categories = typeHint.getMemberCategories();
            assertTrue(categories.contains(MemberCategory.INVOKE_DECLARED_METHODS),
                "setters are not invokable on " + metadataType.getName());
            // Spring instantiates the concrete types through BeanUtils.instantiateClass
            if (!Modifier.isAbstract(metadataType.getModifiers())) {
                assertTrue(categories.contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS),
                    "constructor is not invokable on " + metadataType.getName());
            }
        }
    }

    /**
     * The {@link JmxAttributeSource} return types are exactly the classes Spring populates from
     * the JMX annotations, plus the superclasses holding the shared properties.
     */
    private Set<Class<?>> jmxMetadataTypes() {
        Set<Class<?>> types = new LinkedHashSet<>();
        for (Method method : JmxAttributeSource.class.getMethods()) {
            Class<?> returnType = method.getReturnType();
            if (returnType.isArray()) {
                returnType = returnType.getComponentType();
            }
            for (Class<?> type = returnType; type != null && type != Object.class; type = type.getSuperclass()) {
                types.add(type);
            }
        }
        return types;
    }

    private RuntimeHints applyContribution(DefaultListableBeanFactory beanFactory) {
        BeanFactoryInitializationAotContribution contribution =
            new ManagedResourceHintsAotProcessor().processAheadOfTime(beanFactory);
        assertNotNull(contribution, "expected a contribution for the given bean factory");
        RuntimeHintsOnlyGenerationContext generationContext = new RuntimeHintsOnlyGenerationContext();
        contribution.applyTo(generationContext, null);
        return generationContext.getRuntimeHints();
    }

    private Set<String> methodNames(Set<Method> methods) {
        return methods.stream().map(Method::getName).collect(Collectors.toSet());
    }

    /**
     * The contribution only reads {@link GenerationContext#getRuntimeHints()}, so the rest of
     * the contract is left unimplemented on purpose to keep the test free of extra test jars.
     */
    private static class RuntimeHintsOnlyGenerationContext implements GenerationContext {

        private final RuntimeHints runtimeHints = new RuntimeHints();

        @Override
        public GeneratedClasses getGeneratedClasses() {
            throw new UnsupportedOperationException();
        }

        @Override
        public GeneratedFiles getGeneratedFiles() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RuntimeHints getRuntimeHints() {
            return this.runtimeHints;
        }

        @Override
        public GenerationContext withName(String name) {
            throw new UnsupportedOperationException();
        }

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

    @ManagedResource
    static class BareManagedBean {

        public void notManaged() {
        }

    }

    static class RegularBean {

        @ManagedOperation
        public void ignoredOperation() {
        }

    }

}
