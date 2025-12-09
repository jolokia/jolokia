package org.jolokia.support.jmx;

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

import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.util.HashSet;
import java.util.Set;

import javax.management.*;
import javax.management.modelmbean.ModelMBean;

import org.jolokia.server.core.service.serializer.*;

/**
 * Dedicate MBeanServer for registering Jolokia-only MBeans
 *
 * @author roland
 * @since 11.01.13
 */
class JolokiaMBeanServerHandler implements InvocationHandler {

    private final MBeanServer mBeanServer;
    // MBeanServer to delegate to for JsonMBeans
    private final MBeanServer     delegateServer;
    private final Set<ObjectName> delegatedMBeans;

    private final Serializer serializer;

    /**
     * Create a private MBean server
     */
    public JolokiaMBeanServerHandler(Serializer pSerializer) {
        // uses javax.management.MBeanServerBuilder
        mBeanServer = MBeanServerFactory.newMBeanServer();

        // PlatformMBeanServer is a singleton, but still created using
        // javax.management.MBeanServerFactory.newMBeanServer(java), which involves
        // javax.management.MBeanServerBuilder which may be overriden using -Djavax. management. builder. initial
        delegateServer = ManagementFactory.getPlatformMBeanServer();

        delegatedMBeans = new HashSet<>();
        serializer = pSerializer;
    }

    /** {@inheritDoc} */
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if ("registerMBean".equals(name)) {
            return registerMBean(args[0], (ObjectName) args[1]);
        } else if ("unregisterMBean".equals(name)) {
            unregisterMBean((ObjectName) args[0]);
            return null;
        } else {
            try {
                return method.invoke(mBeanServer,args);
            } catch (InvocationTargetException exp) {
                throw exp.getCause();
            }
        }
    }

    private ObjectInstance registerMBean(Object object, ObjectName name)
            throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        // Register MBean first on this MBean Server
        ObjectInstance ret = mBeanServer.registerMBean(object, name);

        // Check, whether it is annotated with @JsonMBean. Only the outermost class of an inheritance chain is
        // considered.
        JsonMBean anno = extractJsonMBeanAnnotation(object);
        if (anno != null) {
            // The real name can be different than the given one in case the default
            // domain was omitted and/or the MBean implements MBeanRegistration
            ObjectName realName = ret.getObjectName();

            try {
                // Fetch real MBeanInfo and create a dynamic MBean with modified signature
                MBeanInfo info = mBeanServer.getMBeanInfo(realName);
                JsonDynamicMBeanImpl mbean = new JsonDynamicMBeanImpl(mBeanServer,realName,info,serializer,
                                                                      getJsonConverterOptions(anno));

                // Register MBean on delegate MBeanServer
                delegatedMBeans.add(realName);
                delegateServer.registerMBean(mbean,realName);
            } catch (InstanceNotFoundException | IntrospectionException | ReflectionException e) {
                throw new MBeanRegistrationException(e,"Cannot obtain MBeanInfo from Jolokia-Server for " + realName);
            }
        }
        return ret;
    }

    private void unregisterMBean(ObjectName name) throws InstanceNotFoundException, MBeanRegistrationException {
        mBeanServer.unregisterMBean(name);
        if (delegatedMBeans.contains(name)) {
            delegatedMBeans.remove(name);
            delegateServer.unregisterMBean(name);
        }
    }

    // Lookup a JsonMBean annotation
    private JsonMBean extractJsonMBeanAnnotation(Object object) {
        // Try directly
        Class<?> clazz = object.getClass();
        JsonMBean anno =  clazz.getAnnotation(JsonMBean.class);
        if (anno == null && ModelMBean.class.isAssignableFrom(object.getClass())) {
            // For ModelMBean we try some heuristic to get to the managed resource
            // This works for all subclasses of RequiredModelMBean as provided by the JDK
            // but maybe for other ModelMBean classes as well
            Boolean isAccessible = null;
            Field field = null;
            try {
                field = findField(clazz, "managedResource");
                if (field != null) {
                    isAccessible = field.canAccess(object);
                    field.setAccessible(true);
                    Object managedResource = field.get(object);
                    anno = managedResource.getClass().getAnnotation(JsonMBean.class);
                }
            } catch (IllegalAccessException e) {
                // Ignored silently, but we tried it at least
            } catch (RuntimeException e) {
                // See https://openjdk.java.net/jeps/403
                // On JDK9-JDK15 (with --illegal-access=deny) or on JDK-16+ (by default),
                // java.lang.reflect.InaccessibleObjectException is thrown.
                // --illegal-access=permit|warn|debug always ends with at least one WARNING message. The only
                // way to handle the reflection gently is by using:
                //     --illegal-access=deny \
                //     --add-opens java.management/javax.management.modelmbean=ALL-UNNAMED
                // without deny, we can't detect up front (using JDK8 API) if we can safely call setAccessible()...
                if (e.getClass().getName().equals("java.lang.reflect.InaccessibleObjectException")) {
                    // ignore
                    isAccessible = null;
                } else {
                    throw e;
                }
            } finally {
                if (isAccessible != null) {
                    field.setAccessible(isAccessible);
                }
            }
        }
        return anno;
    }

    // Find a field in an inheritance hierarchy
    private Field findField(Class<?> pClazz, String pField) {
        Class<?> c = pClazz;
        do {
            try {
                return c.getDeclaredField(pField);
            } catch (NoSuchFieldException e) {
                c = pClazz.getSuperclass();
            }
        } while (c != null);
        return null;
    }

    // Extract convert options from annotation
    private org.jolokia.core.service.serializer.SerializeOptions getJsonConverterOptions(JsonMBean pAnno) {
        // Extract conversion options from the annotation
        if (pAnno == null) {
            return org.jolokia.core.service.serializer.SerializeOptions.DEFAULT;
        } else {
            org.jolokia.core.service.serializer.ValueFaultHandler faultHandler =
                    pAnno.faultHandling() == JsonMBean.FaultHandler.IGNORE_ERRORS ?
                            org.jolokia.core.service.serializer.ValueFaultHandler.IGNORING_VALUE_FAULT_HANDLER :
                            org.jolokia.core.service.serializer.ValueFaultHandler.THROWING_VALUE_FAULT_HANDLER;
            return new org.jolokia.core.service.serializer.SerializeOptions.Builder()
                    .maxCollectionSize(pAnno.maxCollectionSize())
                    .maxDepth(pAnno.maxDepth())
                    .maxObjects(pAnno.maxObjects())
                    .faultHandler(faultHandler)
                    .build();
        }
    }
}
