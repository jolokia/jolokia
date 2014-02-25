package org.jolokia.jmx;

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

import java.io.ObjectInputStream;
import java.util.Set;

import javax.management.*;
import javax.management.loading.ClassLoaderRepository;

/**
 * A simple proxy which delegates all calls to a delegate. Unfortunately there is no easier yet if
 * one wants to avoid AOP stuff in order to keep deps minimal.
 *
 * @author roland
 * @since 13.01.13
 */
class MBeanServerProxy implements MBeanServer {

    private MBeanServer delegate;

    protected void init(MBeanServer pDelegate) {
        delegate = pDelegate;
    }

    /** {@inheritDoc} */
    public ObjectInstance createMBean(String className, ObjectName name) throws ReflectionException, InstanceAlreadyExistsException, MBeanException, NotCompliantMBeanException {
        return delegate.createMBean(className, name);
    }

    /** {@inheritDoc} */
    public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName) throws ReflectionException, InstanceAlreadyExistsException, MBeanException, NotCompliantMBeanException, InstanceNotFoundException {
        return delegate.createMBean(className, name, loaderName);
    }

    /** {@inheritDoc} */
    public ObjectInstance createMBean(String className, ObjectName name, Object[] params, String[] signature) throws ReflectionException, InstanceAlreadyExistsException, MBeanException, NotCompliantMBeanException {
        return delegate.createMBean(className, name, params, signature);
    }

    /** {@inheritDoc} */
    public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName, Object[] params, String[] signature) throws ReflectionException, InstanceAlreadyExistsException, MBeanException, NotCompliantMBeanException, InstanceNotFoundException {
        return delegate.createMBean(className, name, loaderName, params, signature);
    }

    /** {@inheritDoc} */
    public ObjectInstance registerMBean(Object object, ObjectName name) throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        return delegate.registerMBean(object, name);
    }

    /** {@inheritDoc} */
    public void unregisterMBean(ObjectName name) throws InstanceNotFoundException, MBeanRegistrationException {
        delegate.unregisterMBean(name);
    }

    /** {@inheritDoc} */
    public ObjectInstance getObjectInstance(ObjectName name) throws InstanceNotFoundException {
        return delegate.getObjectInstance(name);
    }

    /** {@inheritDoc} */
    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
        return delegate.queryMBeans(name, query);
    }

    /** {@inheritDoc} */
    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
        return delegate.queryNames(name, query);
    }

    /** {@inheritDoc} */
    public boolean isRegistered(ObjectName name) {
        return delegate.isRegistered(name);
    }

    /** {@inheritDoc} */
    public Integer getMBeanCount() {
        return delegate.getMBeanCount();
    }

    /** {@inheritDoc} */
    public Object getAttribute(ObjectName name, String attribute) throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException {
        return delegate.getAttribute(name, attribute);
    }

    /** {@inheritDoc} */
    public AttributeList getAttributes(ObjectName name, String[] attributes) throws InstanceNotFoundException, ReflectionException {
        return delegate.getAttributes(name, attributes);
    }

    /** {@inheritDoc} */
    public void setAttribute(ObjectName name, Attribute attribute) throws InstanceNotFoundException, AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
        delegate.setAttribute(name, attribute);
    }

    /** {@inheritDoc} */
    public AttributeList setAttributes(ObjectName name, AttributeList attributes) throws InstanceNotFoundException, ReflectionException {
        return delegate.setAttributes(name, attributes);
    }

    /** {@inheritDoc} */
    public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature) throws InstanceNotFoundException, MBeanException, ReflectionException {
        return delegate.invoke(name, operationName, params, signature);
    }

    /** {@inheritDoc} */
    public String getDefaultDomain() {
        return delegate.getDefaultDomain();
    }

    /** {@inheritDoc} */
    public String[] getDomains() {
        return delegate.getDomains();
    }

    /** {@inheritDoc} */
    public void addNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) throws InstanceNotFoundException {
        delegate.addNotificationListener(name, listener, filter, handback);
    }

    /** {@inheritDoc} */
    public void addNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) throws InstanceNotFoundException {
        delegate.addNotificationListener(name, listener, filter, handback);
    }

    /** {@inheritDoc} */
    public void removeNotificationListener(ObjectName name, ObjectName listener) throws InstanceNotFoundException, ListenerNotFoundException {
        delegate.removeNotificationListener(name, listener);
    }

    /** {@inheritDoc} */
    public void removeNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) throws InstanceNotFoundException, ListenerNotFoundException {
        delegate.removeNotificationListener(name, listener, filter, handback);
    }

    /** {@inheritDoc} */
    public void removeNotificationListener(ObjectName name, NotificationListener listener) throws InstanceNotFoundException, ListenerNotFoundException {
        delegate.removeNotificationListener(name, listener);
    }

    /** {@inheritDoc} */
    public void removeNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) throws InstanceNotFoundException, ListenerNotFoundException {
        delegate.removeNotificationListener(name, listener, filter, handback);
    }

    /** {@inheritDoc} */
    public MBeanInfo getMBeanInfo(ObjectName name) throws InstanceNotFoundException, IntrospectionException, ReflectionException {
        return delegate.getMBeanInfo(name);
    }

    /** {@inheritDoc} */
    public boolean isInstanceOf(ObjectName name, String className) throws InstanceNotFoundException {
        return delegate.isInstanceOf(name, className);
    }

    /** {@inheritDoc} */
    public Object instantiate(String className) throws ReflectionException, MBeanException {
        return delegate.instantiate(className);
    }

    /** {@inheritDoc} */
    public Object instantiate(String className, ObjectName loaderName) throws ReflectionException, MBeanException, InstanceNotFoundException {
        return delegate.instantiate(className, loaderName);
    }

    /** {@inheritDoc} */
    public Object instantiate(String className, Object[] params, String[] signature) throws ReflectionException, MBeanException {
        return delegate.instantiate(className, params, signature);
    }

    /** {@inheritDoc} */
    public Object instantiate(String className, ObjectName loaderName, Object[] params, String[] signature) throws ReflectionException, MBeanException, InstanceNotFoundException {
        return delegate.instantiate(className, loaderName, params, signature);
    }

    /** {@inheritDoc} */
    public ObjectInputStream deserialize(ObjectName name, byte[] data) throws OperationsException {
        return delegate.deserialize(name, data);
    }

    /** {@inheritDoc} */
    public ObjectInputStream deserialize(String className, byte[] data) throws OperationsException, ReflectionException {
        return delegate.deserialize(className, data);
    }

    /** {@inheritDoc} */
    public ObjectInputStream deserialize(String className, ObjectName loaderName, byte[] data) throws OperationsException, ReflectionException {
        return delegate.deserialize(className, loaderName, data);
    }

    /** {@inheritDoc} */
    public ClassLoader getClassLoaderFor(ObjectName mbeanName) throws InstanceNotFoundException {
        return delegate.getClassLoaderFor(mbeanName);
    }

    /** {@inheritDoc} */
    public ClassLoader getClassLoader(ObjectName loaderName) throws InstanceNotFoundException {
        return delegate.getClassLoader(loaderName);
    }

    /** {@inheritDoc} */
    public ClassLoaderRepository getClassLoaderRepository() {
        return delegate.getClassLoaderRepository();
    }
}
