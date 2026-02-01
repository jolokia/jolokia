/*
 * Copyright 2009-2025 Roland Huss
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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryEval;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.RuntimeMBeanException;
import javax.management.RuntimeOperationsException;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanOperationInfo;
import javax.management.openmbean.OpenType;
import javax.management.remote.JMXConnector;

import org.jolokia.client.JolokiaClient;
import org.jolokia.client.JolokiaClientBuilder;
import org.jolokia.client.exception.JolokiaBulkRemoteException;
import org.jolokia.client.exception.JolokiaException;
import org.jolokia.client.exception.JolokiaRemoteException;
import org.jolokia.client.request.HttpMethod;
import org.jolokia.client.request.JolokiaExecRequest;
import org.jolokia.client.request.JolokiaListRequest;
import org.jolokia.client.request.JolokiaReadRequest;
import org.jolokia.client.request.JolokiaRequest;
import org.jolokia.client.request.JolokiaSearchRequest;
import org.jolokia.client.response.JolokiaExecResponse;
import org.jolokia.client.response.JolokiaListResponse;
import org.jolokia.client.JolokiaQueryParameter;
import org.jolokia.client.response.JolokiaReadResponse;
import org.jolokia.client.response.JolokiaResponse;
import org.jolokia.client.response.JolokiaSearchResponse;
import org.jolokia.client.request.JolokiaVersionRequest;
import org.jolokia.client.response.JolokiaVersionResponse;
import org.jolokia.client.request.JolokiaWriteRequest;
import org.jolokia.client.response.JolokiaWriteResponse;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.converter.object.Converter;
import org.jolokia.converter.object.ObjectToObjectConverter;
import org.jolokia.converter.object.ObjectToOpenTypeConverter;
import org.jolokia.converter.object.OpenTypeHelper;
import org.jolokia.core.util.ClassUtil;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;

/**
 * <p>{@link MBeanServerConnection} implementation using {@link JolokiaClient}. It is returned from
 * {@link JMXConnector#getMBeanServerConnection()}. It is a partial implementation of "JMX Connector (Client) from
 * JSR-160 Remote JMX specification. Operations which can be mapped to {@link org.jolokia.client.JolokiaOperation}
 * are supported and the remaining ones throw an {@link UnsupportedOperationException} (for example we can't call
 * {@link MBeanServerConnection#createMBean(String, ObjectName)}).</p>
 */
@SuppressWarnings("DuplicatedCode")
public class RemoteJmxAdapter implements MBeanServerConnection {

    private static final Logger LOG = Logger.getLogger("org.jolokia.client.jmx");

    /**
     * Most generic converted for any values (usually Strings) to objects of target class
     * specified as {@link String}. It is used by other, specialized converters.
     */
    private static final Converter<String> objectToObjectConverter;

    /**
     * Deserializer from String, {@link org.jolokia.json.JSONStructure} or other supported objects
     * to objects of class specified as {@link OpenType} for specialized JMX object conversion.
     */
    private static final Converter<OpenType<?>> objectToOpenTypeConverter;

    /**
     * Serializer to JSON values which are sent using {@link org.jolokia.client.JolokiaClient}
     */
    private static final ObjectToJsonConverter toJsonConverter;

    /** Agent ID from Jolokia {@code /version} endpoint */
    private String agentId;

    String agentVersion;
    String protocolVersion;

    /** {@link JolokiaClient} to communicate with the remote Jolokia Agent using JSON/HTTP protocol */
    private final JolokiaClient client;

    /** A set of options sent with every Jolokia request */
    private Map<JolokiaQueryParameter, String> defaultProcessingOptions;

    static {
        // generic converter of any values (primitive, basic like dates and arrays)
        objectToObjectConverter = new ObjectToObjectConverter();

        // set as forgiving, because MBeanInfo for GcInfoCompositeData is inconsistent
        objectToOpenTypeConverter = new ObjectToOpenTypeConverter(objectToObjectConverter, true);

        // default version where CoreConfiguration is not available
        toJsonConverter = new ObjectToJsonConverter((ObjectToObjectConverter) objectToObjectConverter,
            (ObjectToOpenTypeConverter) objectToOpenTypeConverter, null);

        TypeHelper.converter = objectToObjectConverter;
    }

    /**
     * When a client of {@link JMXConnector} uses the related {@link MBeanServerConnection}, it's often
     * required to get an {@link MBeanInfo} for some {@link ObjectName}. It's always worth caching it.
     * I think (prove me wrong) it can be assumed that the target agent does <em>not</em> register
     * completely MBean with different class (and {@link MBeanInfo}) under the same {@link ObjectName}.
     */
    protected final Map<ObjectName, MBeanInfo> mbeanInfoCache = new ConcurrentHashMap<>();

    // helper cache for javax.management.MBeanServerConnection.isInstanceOf()
    // see https://github.com/jolokia/jolokia/issues/666
    private static final Map<ObjectName, Set<String>> platformMBeanInterfaces = new HashMap<>();
    private static ObjectName platformGarbageCollectorMBeanPattern;

    static {
        // here's the hierarchy of interfaces and classes for "platform managed objects"
        // some of these are used by JConsole to monitor an application accessed via remote JMX
        // (both with RMI and Jolokia connectors).
        // Since Jolokia 2.5.0 we can get the information about implemented interfaces for the MBeans, but
        // we should also handle previous Jolokia versions.
        // Here's the hierarchy for OpenJDK Runtime Environment Temurin-17.0.17+10
        //
        // java.lang.management.PlatformManagedObject
        // +-- java.lang.management.CompilationMXBean
        // |   +-- sun.management.CompilationImpl
        // +-- jdk.management.jfr.FlightRecorderMXBean
        // |   +-- jdk.management.jfr.FlightRecorderMXBeanImpl
        // +-- java.lang.management.MemoryMXBean
        // |   +-- sun.management.MemoryImpl
        // +-- java.lang.management.RuntimeMXBean
        // |   +-- sun.management.RuntimeImpl
        // +-- java.lang.management.BufferPoolMXBean
        // |   +-- sun.management.ManagementFactoryHelper
        // +-- java.lang.management.MemoryPoolMXBean
        // |   +-- sun.management.MemoryPoolImpl
        // +-- java.lang.management.PlatformLoggingMXBean
        // |   +-- sun.management.ManagementFactoryHelper.PlatformLoggingImpl
        // +-- com.sun.management.HotSpotDiagnosticMXBean
        // |   +-- com.sun.management.internal.HotSpotDiagnostic
        // +-- java.lang.management.MemoryManagerMXBean
        // |   +-- java.lang.management.GarbageCollectorMXBean
        // |   |   +-- sun.management.GarbageCollectorImpl
        // |   |   |   +-- com.sun.management.internal.GarbageCollectorExtImpl
        // |   |   +-- com.sun.management.GarbageCollectorMXBean
        // |   |       +-- com.sun.management.internal.GarbageCollectorExtImpl
        // |   +-- sun.management.MemoryManagerImpl
        // +-- java.lang.management.ThreadMXBean
        // |   +-- sun.management.ThreadImpl
        // |   |   +-- com.sun.management.internal.HotSpotThreadImpl
        // |   +-- com.sun.management.ThreadMXBean
        // |       +-- com.sun.management.internal.HotSpotThreadImpl
        // +-- java.lang.management.OperatingSystemMXBean
        // |   +-- sun.management.BaseOperatingSystemImpl
        // |   |   +-- com.sun.management.internal.OperatingSystemImpl
        // |   +-- com.sun.management.OperatingSystemMXBean
        // |       +-- com.sun.management.UnixOperatingSystemMXBean
        // |           +-- com.sun.management.internal.OperatingSystemImpl
        // +-- java.lang.management.ClassLoadingMXBean
        //     +-- sun.management.ClassLoadingImpl
        //
        // Here's a fragment for IBM Semeru Runtime Open Edition 17.0.17.0 (build 17.0.17+10)
        // java.lang.management.PlatformManagedObject
        // +-- java.lang.management.OperatingSystemMXBean
        //     +-- com.ibm.java.lang.management.internal.OperatingSystemMXBeanImpl
        //     |   +-- com.ibm.lang.management.internal.ExtendedOperatingSystemMXBeanImpl
        //     |       +-- com.ibm.lang.management.internal.UnixExtendedOperatingSystem
        //     +-- com.sun.management.OperatingSystemMXBean
        //         +-- com.sun.management.UnixOperatingSystemMXBean
        //         |   +-- com.ibm.lang.management.UnixOperatingSystemMXBean
        //         |       +-- com.ibm.lang.management.internal.UnixExtendedOperatingSystem
        //         +-- com.ibm.lang.management.OperatingSystemMXBean
        //             +-- com.ibm.lang.management.internal.ExtendedOperatingSystemMXBeanImpl
        //             |   +-- com.ibm.lang.management.internal.UnixExtendedOperatingSystem
        //             +-- com.ibm.lang.management.UnixOperatingSystemMXBean
        //                 +-- com.ibm.lang.management.internal.UnixExtendedOperatingSystem
        //
        // to satisfy JConsole, which creates MXBean proxies (and verifies the instances) for some platform
        // MBeans we need some mapping. We'll skip javax.management.NotificationBroadcaster/Emitter
        // and JDK specific interfaces (com.sun.management, com.ibm.lang.management, ...)
        //
        // Note: IBM Semeru runtime also supports com.sun.management classes/interfaces and extends them, but
        // we'll hardcode only the default interfaces from java.lang.management package
        try {
            // see sun.tools.jconsole.SummaryTab.formatSummary()
            platformMBeanInterfaces.put(ObjectName.getInstance("java.lang:type=ClassLoading"),
                Set.of(java.lang.management.ClassLoadingMXBean.class.getName()));
            platformMBeanInterfaces.put(ObjectName.getInstance("java.lang:type=Compilation"),
                Set.of(java.lang.management.CompilationMXBean.class.getName()));
            platformMBeanInterfaces.put(ObjectName.getInstance("java.lang:type=Memory"),
                Set.of(java.lang.management.MemoryMXBean.class.getName()));
            platformMBeanInterfaces.put(ObjectName.getInstance("java.lang:type=OperatingSystem"),
                Set.of(java.lang.management.OperatingSystemMXBean.class.getName()));
            platformMBeanInterfaces.put(ObjectName.getInstance("java.lang:type=Runtime"),
                Set.of(java.lang.management.RuntimeMXBean.class.getName()));
            platformMBeanInterfaces.put(ObjectName.getInstance("java.lang:type=Threading"),
                Set.of(java.lang.management.ThreadMXBean.class.getName()));

            // jconsole also checks:
            // - com.sun.management.OperatingSystemMXBean - for java.lang:type=OperatingSystem
            // - java.lang.management.GarbageCollectorMXBean - by java.lang:type=GarbageCollector,* pattern
            platformGarbageCollectorMBeanPattern = ObjectName.getInstance("java.lang:type=GarbageCollector,*");
        } catch (MalformedObjectNameException ignored) {
        }
    }

    /**
     * Create Jolokia backed {@link MBeanServerConnection} using remote Jolokia Agent URI.
     *
     * @param url
     * @throws IOException
     */
    public RemoteJmxAdapter(final String url) throws IOException {
        this(new JolokiaClientBuilder().url(url).build());
    }

    /**
     * Create Jolokia backed {@link MBeanServerConnection} using existing, pre-configured {@link JolokiaClient}
     *
     * @param client
     * @throws IOException
     */
    public RemoteJmxAdapter(final JolokiaClient client) throws IOException {
        this.client = client;
        try {
            JolokiaVersionResponse response = this.unwrapExecute(new JolokiaVersionRequest());
            // Information from Jolokia "version" request.
            agentVersion = response.getAgentVersion();
            protocolVersion = response.getProtocolVersion();
            JSONObject value = response.getValue();
            JSONObject config = (JSONObject) value.get("config");
            agentId = String.valueOf(config == null ? "jolokia-agent" : config.get("agentId"));
            Object version = value.get("agent");
            LOG.info("Remote Agent ID: " + agentId);
            if (version instanceof String v) {
                LOG.info("Remote Agent version: " + v);
            }
        } catch (UncheckedJmxAdapterException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof JMException || cause instanceof JolokiaRemoteException) {
                // panic - handleJolokiaResponseExceptions couldn't find any exception to throw...
                e.throwGenericJMRuntimeCause();
            }
        }
    }

    // hashCode and equals are important, because instances of this connector are cached in
    // com.sun.jmx.mbeanserver.MXBeanLookup.mbscToLookup

    @Override
    public int hashCode() {
        return this.client.getUri().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        // as long as we refer to the same agent, we may be seen as equivalent
        return o instanceof RemoteJmxAdapter && this.client.getUri()
            .equals(((RemoteJmxAdapter) o).client.getUri());
    }

    // --- Unsupported methods of javax.management.MBeanServerConnection

    @Override
    public ObjectInstance createMBean(String className, ObjectName name) {
        throw new UnsupportedOperationException("createMBean not supported over Jolokia");
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName) {
        throw new UnsupportedOperationException("createMBean not supported over Jolokia");
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name, Object[] params, String[] signature) {
        throw new UnsupportedOperationException("createMBean not supported over Jolokia");
    }

    @Override
    public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName, Object[] params, String[] signature) {
        throw new UnsupportedOperationException("createMBean not supported over Jolokia");
    }

    @Override
    public void unregisterMBean(ObjectName name) {
        throw new UnsupportedOperationException("unregisterMBean not supported over Jolokia");
    }

    // --- Supported methods of javax.management.MBeanServerConnection

    // ------ ObjectInstance, MBeanInfo methods

    @Override
    public ObjectInstance getObjectInstance(ObjectName name) throws InstanceNotFoundException, IOException {
        validateNonPatternObjectName(name);

        try {
            JolokiaListResponse listResponse = this.unwrapExecute(new JolokiaListRequest(name), () -> {
                Map<JolokiaQueryParameter, String> options = defaultProcessingOptions();
                // handled by Jolokia 2.5.0+
                options.put(JolokiaQueryParameter.LIST_INTERFACES, "true");
                return options;
            });
            if (listResponse != null) {
                return new ObjectInstance(name, listResponse.getClassName(name));
            }
        } catch (UncheckedJmxAdapterException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                // see com.sun.jmx.interceptor.DefaultMBeanServerInterceptor.rethrow()
                throw new RuntimeMBeanException(runtimeException, runtimeException.getMessage());
            }
            if (cause instanceof InstanceNotFoundException instanceNotFoundException) {
                throw instanceNotFoundException;
            }
            if (cause instanceof JMException || cause instanceof JolokiaRemoteException) {
                // panic - handleJolokiaResponseExceptions couldn't find any exception to throw...
                e.throwGenericJMRuntimeCause();
            }
        }

        throw new InstanceNotFoundException(name.toString());
    }

    @Override
    public boolean isRegistered(ObjectName name) throws IOException {
        validateObjectName(name);

        return !queryNames(name, null).isEmpty();
    }

    @Override
    public boolean isInstanceOf(ObjectName name, String className) throws InstanceNotFoundException, IOException {
        validateNonPatternObjectName(name);

        // the algorithm from the JavaDoc mentions:
        // > let:
        // > X be the MBean named by name
        // > L be the ClassLoader of X
        // there's no way to get the "L" even if we get a full JSON representation of MBeanInfo using
        // Jolokia's list() operation. So we can't do any classloading.
        try {
            JolokiaListResponse listResponse = this.unwrapExecute(new JolokiaListRequest(name), () -> {
                Map<JolokiaQueryParameter, String> options = defaultProcessingOptions();
                // handled by Jolokia 2.5.0+
                options.put(JolokiaQueryParameter.LIST_INTERFACES, "true");
                return options;
            });
            if (listResponse != null) {
                JSONObject info = listResponse.getJSONMBeanInfo(name);
                if (info == null) {
                    // no support from the server side, so we can't do much
                    return false;
                }
                String mbeanClassName = (String) info.get("class");

                boolean interfacesInfo = false;
                final Set<String> interfaces = new HashSet<>();
                if (info.containsKey("interfaces")) {
                    Object interfacesValue = info.get("interfaces");
                    if (interfacesValue instanceof JSONArray array) {
                        interfacesInfo = true;
                        array.forEach(i -> {
                            if (i instanceof String iName) {
                                interfaces.add(iName);
                            }
                        });
                    }
                }
                if (interfacesInfo) {
                    // easier with Jolokia 2.5.0+
                    return interfaces.contains(className) || className.equals(mbeanClassName);
                } else {
                    // this method may be used by jconsole when crating proxies for platform MBeans. So we can
                    // use some hardcoded information
                    Set<String> knownInterfaces = platformMBeanInterfaces.get(name);
                    if (knownInterfaces != null) {
                        return knownInterfaces.contains(className);
                    } else {
                        if (platformGarbageCollectorMBeanPattern.apply(name)) {
                            // special pattern matching for GarbageCollector MBean
                            return java.lang.management.GarbageCollectorMXBean.class.getName().equals(className);
                        }
                        // the only thing we can do is direct check
                        return className.equals(mbeanClassName);
                    }
                }
            }
        } catch (UncheckedJmxAdapterException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                // see com.sun.jmx.interceptor.DefaultMBeanServerInterceptor.rethrow()
                throw new RuntimeMBeanException(runtimeException, runtimeException.getMessage());
            }
            if (cause instanceof InstanceNotFoundException instanceNotFoundException) {
                throw instanceNotFoundException;
            }
            if (cause instanceof JMException || cause instanceof JolokiaRemoteException) {
                // panic - handleJolokiaResponseExceptions couldn't find any exception to throw...
                e.throwGenericJMRuntimeCause();
            }
        }

        throw new InstanceNotFoundException(name.toString());
    }

    @Override
    public MBeanInfo getMBeanInfo(ObjectName name) throws InstanceNotFoundException, ReflectionException, IOException {
        MBeanInfo result = this.mbeanInfoCache.get(name);
        if (result == null) {
            synchronized (mbeanInfoCache) {
                result = this.mbeanInfoCache.get(name);
                if (result == null) {
                    try {
                        final JolokiaListResponse response = this.unwrapExecute(new JolokiaListRequest(name), new Supplier<>() {
                            @Override
                            public Map<JolokiaQueryParameter, String> get() {
                                Map<JolokiaQueryParameter, String> options = defaultProcessingOptions();
                                // handled by Jolokia 2.5.0+
                                options.put(JolokiaQueryParameter.LIST_INTERFACES, "true");
                                // as much as we can, because these nested OpenTypes may be nasty
                                options.put(JolokiaQueryParameter.MAX_DEPTH, "0");
                                return options;
                            }
                        });

                        if (response != null && (result = response.getMBeanInfo(name)) != null) {
                            // cache entire MBeanInfo
                            this.mbeanInfoCache.put(name, result);
                            // cache the types of MBean attributes and operation return values
                            for (MBeanOperationInfo op : result.getOperations()) {
                                // signature needed for overloaded methods
                                final String operationKey = TypeHelper.operationKey(name, op);
                                OpenType<?> retOpenType = op instanceof OpenMBeanOperationInfo openInfo ? openInfo.getReturnOpenType() : null;
                                if (retOpenType == null) {
                                    retOpenType = OpenTypeHelper.findOpenType(op.getDescriptor());
                                }
                                TypeHelper.cache(operationKey, op.getReturnType(), retOpenType);
                            }
                            for (MBeanAttributeInfo attr : result.getAttributes()) {
                                // should be the same (enforced by JMX introspector) for set/get
                                final String attributeKey = TypeHelper.attributeKey(name, attr);
                                OpenType<?> openType = attr instanceof OpenMBeanAttributeInfo openInfo ? openInfo.getOpenType() : null;
                                if (openType == null) {
                                    openType = OpenTypeHelper.findOpenType(attr.getDescriptor());
                                }
                                TypeHelper.cache(attributeKey, attr.getType(), openType);
                            }
                        } else {
                            throw new InstanceNotFoundException(name.toString());
                        }
                    } catch (UncheckedJmxAdapterException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof RuntimeException runtimeException) {
                            // see com.sun.jmx.interceptor.DefaultMBeanServerInterceptor.rethrow()
                            throw new RuntimeMBeanException(runtimeException, runtimeException.getMessage());
                        }
                        if (cause instanceof InstanceNotFoundException instanceNotFoundException) {
                            throw instanceNotFoundException;
                        }
                        if (cause instanceof JMException || cause instanceof JolokiaRemoteException) {
                            // panic - handleJolokiaResponseExceptions couldn't find any exception to throw...
                            e.throwGenericJMRuntimeCause();
                        }
                    }
                }
            }
        }
        return result;
    }

    // ------ Metadata methods

    @Override
    public Integer getMBeanCount() throws IOException {
        return this.queryNames(null, null).size();
    }

    @Override
    public String getDefaultDomain() {
        return "DefaultDomain";
    }

    @Override
    public String[] getDomains() throws IOException {
        Set<String> domains = new TreeSet<>();
        for (final ObjectName name : this.queryNames(null, null)) {
            domains.add(name.getDomain());
        }
        return domains.toArray(new String[0]);
    }

    // ------ Query methods

    @Override
    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) throws IOException {
        final HashSet<ObjectInstance> result = new HashSet<>();

        JolokiaListRequest listRequest = name == null ? new JolokiaListRequest((String) null) : new JolokiaListRequest(name);
        JolokiaListResponse response;
        try {
            response = this.unwrapExecute(listRequest);
        } catch (UncheckedJmxAdapterException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                // see com.sun.jmx.interceptor.DefaultMBeanServerInterceptor.rethrow()
                throw new RuntimeMBeanException(runtimeException, runtimeException.getMessage());
            }
            if (cause instanceof JMException || cause instanceof JolokiaRemoteException) {
                // panic - handleJolokiaResponseExceptions couldn't find any exception to throw...
                e.throwGenericJMRuntimeCause();
            }

            return Collections.emptySet();
        }

        for (ObjectInstance instance : response.getObjectInstances(name)) {
            if (query == null || applyQuery(query, instance.getObjectName())) {
                result.add(instance);
            }
        }

        return result;
    }

    @Override
    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) throws IOException {
        final HashSet<ObjectName> result = new HashSet<>();

        if (name == null) {
            name = getObjectName(""); // same as "*:*"
        }

        final JolokiaSearchRequest j4pSearchRequest = new JolokiaSearchRequest(name);
        JolokiaSearchResponse response;
        try {
            response = unwrapExecute(j4pSearchRequest);
        } catch (UncheckedJmxAdapterException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                // see com.sun.jmx.interceptor.DefaultMBeanServerInterceptor.rethrow()
                throw new RuntimeMBeanException(runtimeException, runtimeException.getMessage());
            }
            if (cause instanceof JMException || cause instanceof JolokiaRemoteException) {
                // panic - handleJolokiaResponseExceptions couldn't find any exception to throw...
                e.throwGenericJMRuntimeCause();
            }

            return Collections.emptySet();
        }

        for (ObjectName objectName : response.getObjectNames()) {
            if (query == null || applyQuery(query, objectName)) {
                result.add(objectName);
            }
        }

        return result;
    }

    // ------ MBean operation/attribute methods - the most important ones

    @Override
    public Object getAttribute(ObjectName name, String attribute)
            throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException, IOException {
        validateNonPatternObjectName(name);

        try {
            JolokiaReadRequest request = new JolokiaReadRequest(name, attribute);
            final JolokiaReadResponse readResponse = unwrapExecute(request, () -> {
                Map<JolokiaQueryParameter, String> options = defaultProcessingOptions();
                // we want to adhere to getAttributes() protocol to ignore missing attributes and instead get
                // some error information (missing? reflection error? ...)
                options.put(JolokiaQueryParameter.IGNORE_ERRORS, "true");
                return options;
            });
            if (readResponse != null) {
                Object value = readResponse.getValue(attribute);
                handleJolokiaResponseExceptions(request, value);

                MBeanAttributeInfo info = getAttributeTypeFromMBeanInfo(name, attribute);
                if (info == null) {
                    throw new AttributeNotFoundException("Can't find MBeanAttributeInfo for " + attribute + " attribute of " + name);
                }
                OpenType<?> openType = info instanceof OpenMBeanAttributeInfo openInfo ? openInfo.getOpenType() : null;
                if (openType == null) {
                    openType = OpenTypeHelper.findOpenType(info.getDescriptor());
                }
                return convertValue(TypeHelper.attributeKey(name, info), info.getType(), openType, value);
            }
        } catch (UncheckedJmxAdapterException e) {
            Throwable cause = e.getCause();
            if (cause instanceof MBeanException mBeanException) {
                throw mBeanException;
            }
            if (cause instanceof AttributeNotFoundException attributeNotFoundException) {
                throw attributeNotFoundException;
            }
            if (cause instanceof InstanceNotFoundException instanceNotFoundException) {
                throw instanceNotFoundException;
            }
            if (cause instanceof ReflectionException reflectionException) {
                throw reflectionException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                // see com.sun.jmx.interceptor.DefaultMBeanServerInterceptor.rethrow()
                throw new RuntimeMBeanException(runtimeException, runtimeException.getMessage());
            }
            if (cause instanceof JMException || cause instanceof JolokiaRemoteException) {
                // panic - handleJolokiaResponseExceptions couldn't find any exception to throw...
                e.throwGenericJMRuntimeCause();
            }
        }

        throw new AttributeNotFoundException("Can't find attribute \"" + attribute + "\" for MBean \""
            + name.getCanonicalName() + "\" (No response received)");
    }

    @Override
    public AttributeList getAttributes(ObjectName name, String[] attributes)
            throws InstanceNotFoundException, ReflectionException, IOException {
        validateNonPatternObjectName(name);

        AttributeList result = new AttributeList();
        try {
            JolokiaReadRequest request = new JolokiaReadRequest(name, attributes);
            final JolokiaReadResponse readResponse = unwrapExecute(request, () -> {
                Map<JolokiaQueryParameter, String> options = defaultProcessingOptions();
                options.put(JolokiaQueryParameter.IGNORE_ERRORS, "true");
                return options;
            });
            if (readResponse != null) {
                Object value = readResponse.getValue();
                handleJolokiaResponseExceptions(request, value);
                List<String> ordered = request.hasSingleAttribute() ? List.of(request.getAttribute()) : new ArrayList<>(request.getAttributes());
                for (String attribute : ordered) {
                    Object v = readResponse.getValue(attribute);
                    if (v instanceof JSONObject json && isJolokiaError(json)) {
                        // just ignore according to javax.management.MBeanServerConnection.getAttributes()
                        continue;
                    }
                    // any other value/type is a proper value
                    MBeanAttributeInfo info = getAttributeTypeFromMBeanInfo(name, attribute);
                    if (info != null) {
                        OpenType<?> openType = info instanceof OpenMBeanAttributeInfo openInfo ? openInfo.getOpenType() : null;
                        if (openType == null) {
                            openType = OpenTypeHelper.findOpenType(info.getDescriptor());
                        }
                        result.add(new Attribute(attribute, convertValue(TypeHelper.attributeKey(name, info), info.getType(),
                            openType, v)));
                    }
                }
            }
        } catch (UncheckedJmxAdapterException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InstanceNotFoundException instanceNotFoundException) {
                throw instanceNotFoundException;
            }
            if (cause instanceof ReflectionException reflectionException) {
                throw reflectionException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                // see com.sun.jmx.interceptor.DefaultMBeanServerInterceptor.rethrow()
                throw new RuntimeMBeanException(runtimeException, runtimeException.getMessage());
            }
            if (cause instanceof JMException || cause instanceof JolokiaRemoteException) {
                // panic - handleJolokiaResponseExceptions couldn't find any exception to throw...
                e.throwGenericJMRuntimeCause();
            }
        }

        return result;
    }

    @Override
    public void setAttribute(ObjectName name, Attribute attribute)
            throws InstanceNotFoundException, AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException, IOException {
        validateNonPatternObjectName(name);

        try {
            JolokiaWriteRequest request = new JolokiaWriteRequest(name, attribute.getName(), attribute.getValue());
            // we don't bother with the returned value, but still handle the Jolokia response
            JolokiaWriteResponse writeResponse = this.unwrapExecute(request);
            if (writeResponse != null) {
                Object value = writeResponse.getValue();
                handleJolokiaResponseExceptions(request, value);
                return;
            }
        } catch (UncheckedJmxAdapterException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InstanceNotFoundException instanceNotFoundException) {
                throw instanceNotFoundException;
            }
            if (cause instanceof AttributeNotFoundException attributeNotFoundException) {
                throw attributeNotFoundException;
            }
            if (cause instanceof InvalidAttributeValueException invalidAttributeValueException) {
                throw invalidAttributeValueException;
            }
            if (cause instanceof MBeanException mBeanException) {
                throw mBeanException;
            }
            if (cause instanceof ReflectionException reflectionException) {
                throw reflectionException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                // see com.sun.jmx.interceptor.DefaultMBeanServerInterceptor.rethrow()
                throw new RuntimeMBeanException(runtimeException, runtimeException.getMessage());
            }
            if (cause instanceof JMException || cause instanceof JolokiaRemoteException) {
                // panic - handleJolokiaResponseExceptions couldn't find any exception to throw...
                e.throwGenericJMRuntimeCause();
            }
        }

        throw new AttributeNotFoundException("Can't write attribute \"" + attribute + "\" for MBean \""
            + name.getCanonicalName() + "\" (No response received)");
    }

    @Override
    public AttributeList setAttributes(ObjectName name, AttributeList attributes)
            throws InstanceNotFoundException, ReflectionException, IOException {
        validateNonPatternObjectName(name);

        // we will return different list - with all the attributes we could actually set
        AttributeList result = new AttributeList();
        try {
            // there's no Jolokia version of write request for multiple attributes, so we have to use bulk requests
            List<JolokiaWriteRequest> attributeWrites = new ArrayList<>(attributes.size());
            for (Attribute attribute : attributes.asList()) {
                // here we don't convert the values - it'll be handled by Jolokia Client itself
                attributeWrites.add(new JolokiaWriteRequest(name, attribute.getName(), attribute.getValue()));
            }
            List<JolokiaWriteResponse> responses;
            try {
                responses = unwrapExecute(attributeWrites, () -> {
                    Map<JolokiaQueryParameter, String> options = defaultProcessingOptions();
                    options.put(JolokiaQueryParameter.IGNORE_ERRORS, "true");
                    return options;
                });
            } catch (JolokiaBulkRemoteException bulkException) {
                // get only successful responses according to javax.management.MBeanServerConnection.setAttributes()
                responses = bulkException.getResponses();
            }
            if (responses != null) {
                for (JolokiaWriteResponse res : responses) {
                    String attribute = res.getRequest().getAttribute();
                    MBeanAttributeInfo info = getAttributeTypeFromMBeanInfo(name, attribute);
                    if (info != null) {
                        // remember to use new value - Jolokia returns old value
                        OpenType<?> openType = info instanceof OpenMBeanAttributeInfo openInfo ? openInfo.getOpenType() : null;
                        if (openType == null) {
                            openType = OpenTypeHelper.findOpenType(info.getDescriptor());
                        }
                        Object value = convertValue(TypeHelper.attributeKey(name, info), info.getType(),
                            openType, res.getValue());
                        result.add(new Attribute(attribute, value));
                    }
                }
            }
        } catch (UncheckedJmxAdapterException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InstanceNotFoundException instanceNotFoundException) {
                throw instanceNotFoundException;
            }
            if (cause instanceof ReflectionException reflectionException) {
                throw reflectionException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                // see com.sun.jmx.interceptor.DefaultMBeanServerInterceptor.rethrow()
                throw new RuntimeMBeanException(runtimeException, runtimeException.getMessage());
            }
            if (cause instanceof JMException || cause instanceof JolokiaRemoteException) {
                // panic - handleJolokiaResponseExceptions couldn't find any exception to throw...
                e.throwGenericJMRuntimeCause();
            }
        }

        return result;
    }

    @Override
    public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature)
            throws InstanceNotFoundException, MBeanException, ReflectionException, IOException {
        validateNonPatternObjectName(name);

        // JVisualVM may send null for no parameters. For us, the signature is more important
        if (signature == null || signature.length == 0) {
            params = new Object[0];
        }

        try {
            JolokiaExecRequest request = new JolokiaExecRequest(name, operationName + TypeHelper.buildSignature(signature), params);
            final JolokiaExecResponse execResponse = unwrapExecute(request);
            if (execResponse != null) {
                Object value = execResponse.getValue();
                handleJolokiaResponseExceptions(request, value);

                MBeanOperationInfo info = getOperationTypeFromMBeanInfo(name, operationName, signature);
                if (info == null) {
                    // see com.sun.jmx.mbeanserver.PerInterface.noSuchMethod
                    NoSuchMethodException cause = new NoSuchMethodException("Can't find MBeanOperationInfo for " + operationName + " operation of " + name);
                    throw new MBeanException(new ReflectionException(cause, cause.getMessage()));
                }
                OpenType<?> retOpenType = info instanceof OpenMBeanOperationInfo openInfo ? openInfo.getReturnOpenType() : null;
                if (retOpenType == null) {
                    retOpenType = OpenTypeHelper.findOpenType(info.getDescriptor());
                }
                return convertValue(TypeHelper.operationKey(name, info), info.getReturnType(), retOpenType, value);
            }
        } catch (UncheckedJmxAdapterException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InstanceNotFoundException instanceNotFoundException) {
                throw instanceNotFoundException;
            }
            if (cause instanceof MBeanException mBeanException) {
                throw mBeanException;
            }
            if (cause instanceof ReflectionException reflectionException) {
                throw reflectionException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                // see com.sun.jmx.interceptor.DefaultMBeanServerInterceptor.rethrow()
                throw new RuntimeMBeanException(runtimeException, runtimeException.getMessage());
            }
            if (cause instanceof JMException || cause instanceof JolokiaRemoteException) {
                // panic - handleJolokiaResponseExceptions couldn't find any exception to throw...
                e.throwGenericJMRuntimeCause();
            }
        }

        NoSuchMethodException cause = new NoSuchMethodException("Can't invoke " + operationName + " of " + name + " (No response received)");
        throw new MBeanException(new ReflectionException(cause, cause.getMessage()));
    }

    // ------ Notification methods

    @Override
    public void addNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) {
        if (!isRunningInJConsole() && !isRunningInJVisualVm() && !isRunningInJmc()) {
            // just ignore in JConsole/JVisualVM as it wrecks the MBean page
            throw new UnsupportedOperationException("addNotificationListener not supported for Jolokia");
        }
    }

    @Override
    public void addNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) {
        throw new UnsupportedOperationException("addNotificationListener not supported for Jolokia");
    }

    @Override
    public void removeNotificationListener(ObjectName name, ObjectName listener) {
        throw new UnsupportedOperationException("removeNotificationListener not supported for Jolokia");
    }

    @Override
    public void removeNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) {
        throw new UnsupportedOperationException("removeNotificationListener not supported by Jolokia");
    }

    @Override
    public void removeNotificationListener(ObjectName name, NotificationListener listener) {
        if (!isRunningInJmc() && !isRunningInJConsole() && !isRunningInJVisualVm()) {
            throw new UnsupportedOperationException("removeNotificationListener not supported by Jolokia");
        }
    }

    @Override
    public void removeNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) {
        throw new UnsupportedOperationException("removeNotificationListener not supported by Jolokia");
    }

    // ------ Private/helper methods for handling JolokiaClient

    private boolean isRunningInJConsole() {
        return System.getProperty("jconsole.showOutputViewer") != null;
    }

    private boolean isRunningInJVisualVm() {
        final String version = System.getProperty("netbeans.productversion");
        return version != null && version.contains("VisualVM");
    }

    private boolean isRunningInJmc() {
        return System.getProperty("running.in.jmc") != null;
    }

    /**
     * While {@link JolokiaClient} can handle patterns or "all objects" requests, {@link MBeanServerConnection}
     * contract is more strict.
     *
     * @param name
     * @throws InstanceNotFoundException
     */
    private void validateNonPatternObjectName(ObjectName name) throws InstanceNotFoundException {
        if (name == null) {
            throw new RuntimeOperationsException(new IllegalArgumentException("Object name cannot be null"));
        }
        if (name.isPattern()) {
            throw new InstanceNotFoundException(name.toString());
        }
    }

    private void validateObjectName(ObjectName name) {
        if (name == null) {
            throw new RuntimeOperationsException(new IllegalArgumentException("Object name cannot be null"));
        }
    }

    /**
     * Default set of options to pass to {@link JolokiaClient#execute(JolokiaRequest, Map)}
     * @return
     */
    Map<JolokiaQueryParameter, String> defaultProcessingOptions() {
        if (defaultProcessingOptions == null) {
            defaultProcessingOptions = new HashMap<>();
            defaultProcessingOptions.put(JolokiaQueryParameter.LIST_CACHE, "true");
            // in case of stack overflow it's 10, but for list responses we need max=0
            defaultProcessingOptions.put(JolokiaQueryParameter.MAX_DEPTH, "10");
            defaultProcessingOptions.put(JolokiaQueryParameter.SERIALIZE_EXCEPTION, "true");
        }
        return defaultProcessingOptions;
    }

    /**
     * <p>Process {@link JolokiaResponse#getValue()} and if it contains error fields ("status", "error", ...) try
     * to build and throw proper Java exceptions wrapped in {@link UncheckedJmxAdapterException}.<br/>
     * This method is called after {@link JolokiaClient} does <em>not</em> throw {@link JolokiaException}.</p>
     *
     * <p>If the {@link JolokiaRequest} is passed and we can't extract (and throw) any exception based on the
     * {@code value}, we throw {@link JolokiaRemoteException} wrapped in {@link UncheckedJmxAdapterException} to be
     * processed by the caller. In other words - if the cause of {@link UncheckedJmxAdapterException} is
     * {@link JolokiaRemoteException}, there's something missing in the JSON error response.</p>
     *
     * @param request
     * @param value
     * @return
     */
    private void handleJolokiaResponseExceptions(JolokiaRequest request, Object value) throws UncheckedJmxAdapterException {
        if (!(value instanceof JSONObject json)) {
            return;
        }

        if (!isJolokiaError(json)) {
            return;
        }

        // try to throw some exception
        String msg = json.get("error") instanceof String errorMessage ? errorMessage : "Jolokia error";
        Class<Exception> exceptionClass = null;
        if (json.get("error_type_jmx") instanceof String jmxExceptionClassName) {
            exceptionClass = ClassUtil.classForName(jmxExceptionClassName);
        } else if (json.get("error_type") instanceof String someExceptionClassName) {
            exceptionClass = ClassUtil.classForName(someExceptionClassName);
        }
        if (exceptionClass != null) {
            try {
                Constructor<Exception> c = exceptionClass.getConstructor(String.class);
                throw new UncheckedJmxAdapterException(c.newInstance(msg));
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        // still nothing thrown?
        if (request != null) {
            // there was some request that caused this exception, but we couldn't check what was wrong in the
            // JSON error message
            throw new UncheckedJmxAdapterException(new JolokiaRemoteException(request, json));
        }
    }

    /**
     * {@link JSONObject} could be a normal Jolokia response, but some fields indicate it's actually an error.
     *
     * @param json
     * @return
     */
    private boolean isJolokiaError(JSONObject json) {
        // Jolokia 2.4.0+
        boolean isError = json.containsKey(".error");
        if (!isError) {
            isError = json.containsKey("error") && (json.containsKey("error_type") || json.containsKey("error_type_jmx"));
        }

        return isError;
    }

    /**
     * Call {@link JolokiaClient#execute(JolokiaRequest)} sending a prepared {@link JolokiaRequest} and handling
     * possible exceptions.
     *
     * @param pRequest
     * @return
     * @param <RESP>
     * @param <REQ>
     */
    private <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    RESP unwrapExecute(REQ pRequest)
            throws IOException, UncheckedJmxAdapterException {
        return unwrapExecute(pRequest, new Supplier<>() {
            @Override
            public Map<JolokiaQueryParameter, String> get() {
                return defaultProcessingOptions();
            }
        });
    }

    /**
     * Call {@link JolokiaClient#execute(JolokiaRequest)} sending a prepared {@link JolokiaRequest} and handling
     * possible exceptions. This methods accepts a {@link Supplier} to customize Jolokia request options.
     *
     * @param pRequest
     * @param optionsProvider
     * @return
     * @param <RESP>
     * @param <REQ>
     */
    private <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    RESP unwrapExecute(REQ pRequest, Supplier<Map<JolokiaQueryParameter, String>> optionsProvider)
            throws IOException, UncheckedJmxAdapterException {
        try {
            pRequest.setPreferredHttpMethod(HttpMethod.POST);
            Map<JolokiaQueryParameter, String> options = optionsProvider == null ? defaultProcessingOptions() : optionsProvider.get();

            return this.client.execute(pRequest, options);
        } catch (JolokiaException e) {
            handleException(e);
            // not-reachable
            return null;
        }
    }

    /**
     * Call {@link JolokiaClient#execute(List)} sending a prepared bulk {@link JolokiaRequest} and handling
     * possible exceptions.
     *
     * @param pRequests
     * @return
     * @param <RESP>
     * @param <REQ>
     */
    private <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    List<RESP> unwrapExecute(List<REQ> pRequests)
            throws IOException, UncheckedJmxAdapterException, JolokiaBulkRemoteException {
        return unwrapExecute(pRequests, new Supplier<>() {
            @Override
            public Map<JolokiaQueryParameter, String> get() {
                return defaultProcessingOptions();
            }
        });
    }

    /**
     * Call {@link JolokiaClient#execute(List)} sending a prepared bulk {@link JolokiaRequest} and handling
     * possible exceptions. This methods accepts a {@link Supplier} to customize Jolokia request options.
     *
     * @param pRequests
     * @param optionsProvider
     * @return
     * @param <RESP>
     * @param <REQ>
     */
    private <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    List<RESP> unwrapExecute(List<REQ> pRequests, Supplier<Map<JolokiaQueryParameter, String>> optionsProvider)
            throws IOException, UncheckedJmxAdapterException, JolokiaBulkRemoteException {
        try {
            Map<JolokiaQueryParameter, String> options = optionsProvider == null ? defaultProcessingOptions() : optionsProvider.get();
            return this.client.execute(pRequests, options);
        } catch (JolokiaBulkRemoteException e) {
            // because the exception may contain intermixed JSON responses and errors
            throw e;
        } catch (JolokiaException e) {
            handleException(e);
            // not-reachable
            return null;
        }
    }

    /**
     * Handle an exception from the remote Jolokia call performed by {@link JolokiaClient} by throwing an
     * exception to be handled by actual implementations of {@link MBeanServerConnection} interface methods, according
     * to the method prototype. If the caller should handle relevant exceptions, it can find it inside
     * {@link UncheckedJmxAdapterException}.
     *
     * @param e an exception thrown by {@link JolokiaClient}.
     * @throws IOException
     * @throws InstanceNotFoundException
     */
    protected void handleException(JolokiaException e) throws IOException, UncheckedJmxAdapterException {
        // Here's the summary of the exceptions thrown by supported/implemented methods of MBeanServerConnection:
        // - getObjectInstance: IOException, InstanceNotFoundException
        // - isRegistered:      IOException
        // - isInstanceOf:      IOException, InstanceNotFoundException
        // - getMBeanInfo:      IOException, InstanceNotFoundException, IntrospectionException, ReflectionException
        // - getMBeanCount:     IOException
        // - getDefaultDomain:  IOException
        // - getDomains:        IOException
        // - queryMBeans:       IOException
        // - queryNames:        IOException
        // - getAttribute   IOException, InstanceNotFoundException, ReflectionException, MBeanException, AttributeNotFoundException
        // - getAttributes: IOException, InstanceNotFoundException, ReflectionException
        // - setAttribute:  IOException, InstanceNotFoundException, ReflectionException, MBeanException, AttributeNotFoundException, InvalidAttributeValueException
        // - setAttributes: IOException, InstanceNotFoundException, ReflectionException
        // - invoke:        IOException, InstanceNotFoundException, ReflectionException, MBeanException
        // - addNotificationListener:    IOException, InstanceNotFoundException
        // - removeNotificationListener: IOException, InstanceNotFoundException, ListenerNotFoundException
        // and that's what we're trying to recover from the error JSON response

        if (e.getCause() instanceof IOException ioException) {
            throw ioException;
        } else if (e.getCause() instanceof RuntimeException runtimeException) {
            throw runtimeException;
        } else if (e.getCause() instanceof Error error) {
            throw error;
        } else if (e instanceof JolokiaRemoteException jolokiaRemoteException) {
            // we may have a JSON error object
            if (jolokiaRemoteException.getResponse() != null) {
                handleJolokiaResponseExceptions(null, jolokiaRemoteException.getResponse());
            }
        }

        throw new UncheckedJmxAdapterException(e);
    }

    // ------ Helper methods for deal with MBeanInfo and type mapping

    /**
     * Get an {@link MBeanAttributeInfo} for an attribute of an MBean
     *
     * @param objectName
     * @param attributeName
     * @return
     * @throws IOException
     * @throws InstanceNotFoundException
     */
    private MBeanAttributeInfo getAttributeTypeFromMBeanInfo(final ObjectName objectName, final String attributeName)
            throws IOException, InstanceNotFoundException, ReflectionException {
        final MBeanInfo mBeanInfo = getMBeanInfo(objectName);
        for (final MBeanAttributeInfo attribute : mBeanInfo.getAttributes()) {
            if (attributeName.equals(attribute.getName())) {
                return attribute;
            }
        }

        return null;
    }

    /**
     * Get an {@link MBeanOperationInfo} for an operation of an MBean
     *
     * @param objectName
     * @param operationName
     * @param signature
     * @return
     * @throws IOException
     * @throws InstanceNotFoundException
     */
    private MBeanOperationInfo getOperationTypeFromMBeanInfo(ObjectName objectName, String operationName, String[] signature)
            throws IOException, InstanceNotFoundException, ReflectionException {
        final MBeanInfo mBeanInfo = getMBeanInfo(objectName);
        if (signature == null) {
            signature = new String[0];
        }
        for (final MBeanOperationInfo operation : mBeanInfo.getOperations()) {
            if (operationName.equals(operation.getName()) &&
                Arrays.equals(Arrays.stream(operation.getSignature()).map(MBeanParameterInfo::getType).toArray(String[]::new), signature)) {
                return operation;
            }
        }

        return null;
    }

    /**
     * Helper method to apply a {@link QueryExp} with correct thread-local {@link MBeanServer}.
     * @param query
     * @param objectName
     * @return
     */
    private boolean applyQuery(QueryExp query, ObjectName objectName) {
        if (QueryEval.getMBeanServer() == null) {
            query.setMBeanServer(this.createStandInMbeanServerProxyForQueryEvaluation());
        }
        try {
            return query.apply(objectName);
        } catch (Exception ignored) {
            // 7.5.3 Query Exceptions
            // If the evaluation of a query for a given MBean produces one of these
            // exceptions, the MBean is omitted from the query result. Application code will not
            // see these exceptions in usual circumstances. Only if the application itself throws the
            // exception, or if it calls QueryExp.apply, will it see these exceptions.
            return false;
        }
    }

    /**
     * Query evaluation requires an MBeanServer but only to figure out objectInstance
     *
     * @return a dynamic proxy dispatching getObjectInstance back to myself
     */
    private MBeanServer createStandInMbeanServerProxyForQueryEvaluation() {
        return (MBeanServer) Proxy.newProxyInstance(this.getClass().getClassLoader(),
            new Class[]{MBeanServer.class},
            (proxy, method, args) -> {
                if (method.getName().contains("getObjectInstance") && args.length == 1) {
                    return getObjectInstance((ObjectName) args[0]);
                } else {
                    throw new UnsupportedOperationException(
                        "This MBeanServer proxy does not support " + method);
                }
            });
    }

    /**
     * Handle data conversion using type information collected from {@link MBeanInfo}.
     *
     * @param cacheKey Jolokia-specific cache key for attribute or operation (overloaded)
     * @param typeName from {@link MBeanAttributeInfo#getType()} or from {@link MBeanOperationInfo#getReturnType()}
     * @param rawValue
     * @return
     * @throws ReflectionException
     */
    Object convertValue(String cacheKey, String typeName, OpenType<?> openType, Object rawValue) throws ReflectionException {
        CachedType entry = TypeHelper.cache(cacheKey, typeName, openType);
        if (entry.type() == null) {
            // cached as unresolved type
            ClassNotFoundException cause = new ClassNotFoundException("Can't resolve " + entry.typeName());
            throw new ReflectionException(cause, cause.getMessage());
        }
        if (entry.openType() == null) {
            // we don't have a real OpenType to convert to; all we need is the data itself. This may
            // happen for example with non-MXBeans which return CompositeData/TabularData directly.
            // real MXBeans should return Maps/Lists/Objects which are converted to "easy" tabular/composite data
            // with "key" and "value" items.
            // Jolokia 2.5.0 can return OpenType information with list responses, but we want to be able to
            // use older Jolokia Agents too
            OpenType<?> discoveredOpenType = TypeHelper.buildOpenType(typeName, rawValue);
            if (discoveredOpenType != null) {
                // we can fix the cache with a new OpenType for this key
                entry = TypeHelper.cache(cacheKey, typeName, discoveredOpenType);
            }
        }
        try {
            if (entry.openType() != null) {
                return objectToOpenTypeConverter.convert(entry.openType(), rawValue);
            } else {
                return objectToObjectConverter.convert(entry.type().getName(), rawValue);
            }
        } catch (IllegalArgumentException e) {
            throw new UncheckedJmxAdapterException(new ReflectionException(e, e.getMessage()));
        }
    }

    static ObjectName getObjectName(String objectName) {
        try {
            return ObjectName.getInstance(objectName);
        } catch (MalformedObjectNameException e) {
            throw new UncheckedJmxAdapterException(e);
        }
    }

    String getId() {
        return this.agentId;
    }

}
