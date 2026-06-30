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
import java.util.LinkedHashSet;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedMetric;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Registers reflection hints for Spring JMX methods on {@link ManagedResource} beans.
 * Jolokia invokes these methods through the JMX layer when it serves operation and
 * attribute requests in a native image.
 */
public class ManagedResourceHintsAotProcessor implements BeanFactoryInitializationAotProcessor {

    @Override
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        Set<Method> managedMethods = findManagedMethods(beanFactory);
        return managedMethods.isEmpty() ? null : new ManagedResourceHintsContribution(managedMethods);
    }

    static Set<Method> findManagedMethods(ConfigurableListableBeanFactory beanFactory) {
        Set<Method> managedMethods = new LinkedHashSet<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType != null) {
                Class<?> userClass = ClassUtils.getUserClass(beanType);
                if (AnnotationUtils.findAnnotation(userClass, ManagedResource.class) != null) {
                    collectManagedMethods(userClass, managedMethods);
                }
            }
        }
        return managedMethods;
    }

    private static void collectManagedMethods(Class<?> userClass, Set<Method> managedMethods) {
        ReflectionUtils.doWithMethods(userClass, method -> {
            Method specificMethod = ClassUtils.getMostSpecificMethod(method, userClass);
            Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(specificMethod);
            if (isManagedMethod(method) || isManagedMethod(specificMethod) || isManagedMethod(bridgedMethod)) {
                managedMethods.add(bridgedMethod);
            }
        }, method -> !method.isSynthetic() && !method.isBridge());
    }

    private static boolean isManagedMethod(Method method) {
        return AnnotationUtils.findAnnotation(method, ManagedOperation.class) != null
                || AnnotationUtils.findAnnotation(method, ManagedAttribute.class) != null
                || AnnotationUtils.findAnnotation(method, ManagedMetric.class) != null;
    }

    private record ManagedResourceHintsContribution(
            Set<Method> managedMethods) implements BeanFactoryInitializationAotContribution {

        @Override
        public void applyTo(GenerationContext generationContext,
                            @NonNull BeanFactoryInitializationCode beanFactoryInitializationCode) {
            ReflectionHints hints = generationContext.getRuntimeHints().reflection();
            this.managedMethods.forEach(method -> hints.registerMethod(method, ExecutableMode.INVOKE));
        }

    }

}
