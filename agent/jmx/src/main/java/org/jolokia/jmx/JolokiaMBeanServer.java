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

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import javax.management.*;
import javax.management.modelmbean.ModelMBean;
import javax.management.openmbean.OpenType;

import org.jolokia.converter.Converters;
import org.jolokia.converter.json.JsonConvertOptions;
import org.jolokia.converter.json.ValueFaultHandler;

/**
 * Dedicate MBeanServer for registering Jolokia-only MBeans
 *
 * @author roland
 * @since 11.01.13
 */
class JolokiaMBeanServer extends MBeanServerProxy {

    // MBeanServer to delegate to for JsonMBeans
    private MBeanServer     delegateServer;
    private Set<ObjectName> delegatedMBeans;

    private Converters converters;

    /**
     * Create a private MBean server
     */
    public JolokiaMBeanServer() {
        MBeanServer mBeanServer = MBeanServerFactory.newMBeanServer();
        delegatedMBeans = new HashSet<ObjectName>();
        delegateServer = ManagementFactory.getPlatformMBeanServer();
        converters = new Converters();
        init(mBeanServer);
    }

    @Override
    public ObjectInstance registerMBean(Object object, ObjectName name)
            throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        // Register MBean first on this MBean Server
        ObjectInstance ret = super.registerMBean(object, name);

        // Check, whether it is annotated with @JsonMBean. Only the outermost class of an inheritance is
        // considered.
        JsonMBean anno = extractJsonMBeanAnnotation(object);
        if (anno != null) {
            // The real name can be different than the given one in case the default
            // domain was omitted and/or the MBean implements MBeanRegistration
            ObjectName realName = ret.getObjectName();

            try {
                // Fetch real MBeanInfo and create a dynamic MBean with modified signature
                MBeanInfo info = super.getMBeanInfo(realName);
                JsonDynamicMBeanImpl mbean = new JsonDynamicMBeanImpl(this,realName,info,getJsonConverterOptions(anno));

                // Register MBean on delegate MBeanServer
                delegatedMBeans.add(realName);
                delegateServer.registerMBean(mbean,realName);
            } catch (InstanceNotFoundException e) {
                throw new MBeanRegistrationException(e,"Cannot obtain MBeanInfo from Jolokia-Server for " + realName);
            } catch (IntrospectionException e) {
                throw new MBeanRegistrationException(e,"Cannot obtain MBeanInfo from Jolokia-Server for " + realName);
            } catch (ReflectionException e) {
                throw new MBeanRegistrationException(e,"Cannot obtain MBeanInfo from Jolokia-Server for " + realName);
            }
        }
        return ret;
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
                    isAccessible = field.isAccessible();
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
        Class c = pClazz;
        do {
            try {
                return c.getDeclaredField(pField);
            } catch (NoSuchFieldException e) {
                c = pClazz.getSuperclass();
            }
        } while (c != null);
        return null;
    }

    @Override
    public void unregisterMBean(ObjectName name) throws InstanceNotFoundException, MBeanRegistrationException {
        super.unregisterMBean(name);
        if (delegatedMBeans.contains(name)) {
            delegatedMBeans.remove(name);
            delegateServer.unregisterMBean(name);
        }
    }

    /**
     * Converter used by JsonMBean for converting from Object to JSON representation
     *
     * @param object object to serialize
     * @param pConvertOptions options used for conversion
     * @return serialized object
     */
    String toJson(Object object, JsonConvertOptions pConvertOptions) {
        try {
            Object ret = converters.getToJsonConverter().convertToJson(object,null,pConvertOptions);
            return ret.toString();
        } catch (AttributeNotFoundException exp) {
            // Cannot happen, since we dont use a path
            return "";
        }
    }

    /**
     * Convert from a JSON or other string representation to real object. Used when preparing operation
     * argument. If the JSON structure cannot be converted, an {@link IllegalArgumentException} is thrown.
     *
     * @param type type to convert to
     * @param json string to deserialize
     * @return the deserialized object
     */
    Object fromJson(String type, String json) {
        return converters.getToObjectConverter().convertFromString(type,json);
    }

    /**
     * Convert from JSON for OpenType objects. Throws an {@link IllegalArgumentException} if
     *
     * @param type open type
     * @param json JSON representation to convert from
     * @return the converted object
     */
    Object fromJson(OpenType type, String json) {
        return converters.getToOpenTypeConverter().convertToObject(type,json);
    }

    // Extract convert options from annotation
    private JsonConvertOptions getJsonConverterOptions(JsonMBean pAnno) {
        // Extract conversion options from the annotation
        if (pAnno == null) {
            return JsonConvertOptions.DEFAULT;
        } else {
            ValueFaultHandler faultHandler =
                    pAnno.faultHandling() == JsonMBean.FaultHandler.IGNORE_ERRORS ?
                            ValueFaultHandler.IGNORING_VALUE_FAULT_HANDLER :
                            ValueFaultHandler.THROWING_VALUE_FAULT_HANDLER;
            return new JsonConvertOptions.Builder()
                    .maxCollectionSize(pAnno.maxCollectionSize())
                    .maxDepth(pAnno.maxDepth())
                    .maxObjects(pAnno.maxObjects())
                    .faultHandler(faultHandler)
                    .build();
        }
    }


}
