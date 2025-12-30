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
import java.lang.management.GarbageCollectorMXBean;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
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
import javax.management.RuntimeMBeanException;
import javax.management.openmbean.InvalidOpenTypeException;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;

import org.jolokia.client.JolokiaClient;
import org.jolokia.client.JolokiaClientBuilder;
import org.jolokia.client.exception.JolokiaBulkRemoteException;
import org.jolokia.client.exception.JolokiaException;
import org.jolokia.client.exception.JolokiaRemoteException;
import org.jolokia.client.exception.UncheckedJmxAdapterException;
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
import org.jolokia.core.util.ClassUtil;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;

/**
 * <p>I emulate a subset of the functionality of a native {@link MBeanServerConnection} but over a Jolokia
 * connection to remote Jolokia Agent, the response types and thrown exceptions attempts to mimic as close as
 * possible the ones from a native Java {@link MBeanServerConnection}</p>
 *
 * <p>Operations that are not supported will throw an {@link UnsupportedOperationException}.</p>
 */
public class RemoteJmxAdapter implements MBeanServerConnection {

    // Information retrieved from Jolokia "version" request.
    String agentVersion;
    String protocolVersion;
    private String agentId;

    private final JolokiaClient connector;
    private HashMap<JolokiaQueryParameter, String> defaultProcessingOptions;
    protected final Map<ObjectName, MBeanInfo> mbeanInfoCache = new HashMap<>();

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
     * Create Jolokia backed {@link MBeanServerConnection} using existing {@link JolokiaClient}
     *
     * @param connector
     * @throws IOException
     */
    public RemoteJmxAdapter(final JolokiaClient connector) throws IOException {
        this.connector = connector;
        try {
            JolokiaVersionResponse response = this.unwrapExecute(new JolokiaVersionRequest());
            this.agentVersion = response.getAgentVersion();
            this.protocolVersion = response.getProtocolVersion();
            JSONObject value = response.getValue();
            JSONObject config = (JSONObject) value.get("config");
            this.agentId = String.valueOf(config.get("agentId"));
        } catch (InstanceNotFoundException ignore) {
        }
    }

    @Override
    public int hashCode() {
        return this.connector.getUri().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        //as long as we refer to the same agent, we may be seen as equivalent
        return o instanceof RemoteJmxAdapter && this.connector.getUri()
            .equals(((RemoteJmxAdapter) o).connector.getUri());
    }

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
    public ObjectInstance createMBean(String className, ObjectName name,
                                      ObjectName loaderName, Object[] params, String[] signature) {
        throw new UnsupportedOperationException("createMBean not supported over Jolokia");
    }

    @Override
    public void unregisterMBean(ObjectName name) {
        throw new UnsupportedOperationException("unregisterMBean not supported over Jolokia");
    }

    @Override
    public ObjectInstance getObjectInstance(ObjectName name)
        throws InstanceNotFoundException, IOException {

        JolokiaListResponse listResponse = this.unwrapExecute(new JolokiaListRequest(name), () -> {
            Map<JolokiaQueryParameter, String> options = defaultProcessingOptions();
            // handled by Jolokia 2.5.0+
            options.put(JolokiaQueryParameter.LIST_INTERFACES, "true");
            return options;
        });
        return new ObjectInstance(name, listResponse.getClassName(name));
    }

    private List<ObjectInstance> listObjectInstances(ObjectName name) throws IOException {
        JolokiaListRequest listRequest = name == null
            ? new JolokiaListRequest((String) null)
            : new JolokiaListRequest(name);
        try {
            final JolokiaListResponse listResponse = this.unwrapExecute(listRequest);
            return listResponse.getObjectInstances(name);
        } catch (MalformedObjectNameException e) {
            throw new UncheckedJmxAdapterException(e);
        } catch (InstanceNotFoundException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) throws IOException {
        final HashSet<ObjectInstance> result = new HashSet<>();

        for (ObjectInstance instance : this.listObjectInstances(name)) {
            if (query == null || applyQuery(query, instance.getObjectName())) {
                result.add(instance);
            }
        }
        return result;
    }

    @Override
    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) throws IOException {

        final HashSet<ObjectName> result = new HashSet<>();
        // if name is null, use list instead of search
        if (name == null) {
            try {
                name = getObjectName("");
            } catch (UncheckedJmxAdapterException ignore) {
            }
        }

        final JolokiaSearchRequest j4pSearchRequest = new JolokiaSearchRequest(name);
        JolokiaSearchResponse response;
        try {
            response = unwrapExecute(j4pSearchRequest);
        } catch (InstanceNotFoundException e) {
            return Collections.emptySet();
        }
        final List<ObjectName> names = response.getObjectNames();

        for (ObjectName objectName : names) {
            if (query == null || applyQuery(query, objectName)) {
                result.add(objectName);
            }
        }

        return result;
    }

    private boolean applyQuery(QueryExp query, ObjectName objectName) {
        if (QueryEval.getMBeanServer() == null) {
            query.setMBeanServer(this.createStandInMbeanServerProxyForQueryEvaluation());
        }
        try {
            return query.apply(objectName);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UncheckedJmxAdapterException(e);
        }
    }

    /**
     * Query evaluation requires an MBeanServer but only to figure out objectInstance
     *
     * @return a dynamic proxy dispatching getObjectInstance back to myself
     */
    private MBeanServer createStandInMbeanServerProxyForQueryEvaluation() {
        return (MBeanServer)
            Proxy.newProxyInstance(
                this.getClass().getClassLoader(),
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

    private <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest> RESP unwrapExecute(REQ pRequest)
            throws IOException, InstanceNotFoundException {
        return unwrapExecute(pRequest, new Supplier<>() {
            @Override
            public Map<JolokiaQueryParameter, String> get() {
                return defaultProcessingOptions();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest> RESP unwrapExecute(REQ pRequest, Supplier<Map<JolokiaQueryParameter, String>> optionsProvider)
            throws IOException, InstanceNotFoundException {
        try {
            pRequest.setPreferredHttpMethod(HttpMethod.POST);
            return this.connector.execute(pRequest, optionsProvider == null ? defaultProcessingOptions() : optionsProvider.get());
        } catch (JolokiaException e) {
            return (RESP) unwrapException(e);
        }
    }

    private Map<JolokiaQueryParameter, String> defaultProcessingOptions() {
        if (defaultProcessingOptions == null) {
            defaultProcessingOptions = new HashMap<>();
            defaultProcessingOptions.put(JolokiaQueryParameter.MAX_DEPTH, "10"); // in case of stack overflow
            defaultProcessingOptions.put(JolokiaQueryParameter.SERIALIZE_EXCEPTION, "true");
        }
        return defaultProcessingOptions;
    }

    private static final Set<String> UNCHECKED_REMOTE_EXCEPTIONS =
        Collections.singleton("java.lang.UnsupportedOperationException");

    @SuppressWarnings("rawtypes")
    protected JolokiaResponse unwrapException(JolokiaException e) throws IOException, InstanceNotFoundException {
        if (e.getCause() instanceof IOException) {
            throw (IOException) e.getCause();
        } else if (e.getCause() instanceof RuntimeException) {
            throw (RuntimeException) e.getCause();
        } else if (e.getCause() instanceof Error) {
            throw (Error) e.getCause();
        } else if (e.getMessage()
            .matches("Error: java.lang.IllegalArgumentException : No MBean '.+' found")) {
            throw new InstanceNotFoundException();
        } else if (e instanceof JolokiaRemoteException
            && UNCHECKED_REMOTE_EXCEPTIONS.contains(((JolokiaRemoteException) e).getErrorType())) {
            throw new RuntimeMBeanException(
                ClassUtil.newInstance(((JolokiaRemoteException) e).getErrorType(), e.getMessage()));
        } else {
            throw new UncheckedJmxAdapterException(e);
        }
    }

    @Override
    public boolean isRegistered(ObjectName name) throws IOException {

        return !queryNames(name, null).isEmpty();
    }

    @Override
    public Integer getMBeanCount() throws IOException {
        return this.queryNames(null, null).size();
    }

    @Override
    public Object getAttribute(ObjectName name, String attribute)
        throws AttributeNotFoundException, InstanceNotFoundException, IOException {
        try {
            final Object rawValue = unwrapExecute(new JolokiaReadRequest(name, attribute)).getValue();
            return adaptJsonToOptimalResponseValue(name, attribute, rawValue, getAttributeTypeFromMBeanInfo(name, attribute));
        } catch (UncheckedJmxAdapterException e) {
            if (e.getCause() instanceof JolokiaRemoteException
                && "javax.management.AttributeNotFoundException"
                .equals(((JolokiaRemoteException) e.getCause()).getErrorType())) {
                throw new AttributeNotFoundException((e.getCause().getMessage()));
            } else {
                throw e;
            }
        } catch (UnsupportedOperationException e) {
            //JConsole does not seem to like unsupported operation while looking up attributes
            throw new AttributeNotFoundException();
        }
    }

    private Object adaptJsonToOptimalResponseValue(
        ObjectName name, String attribute, Object rawValue, String typeFromMBeanInfo)
        throws IOException, InstanceNotFoundException {
        final String qualifiedName = name + "." + attribute;

        //cache MBeanInfo (and thereby attribute types) if it is not yet cached
        getMBeanInfo(name);

        //adjust numeric types, to avoid ClassCastException e.g. in JConsole proxies
        if (this.isPrimitive(rawValue)) {
            OpenType<?> attributeType = null;
            try {
                attributeType = ToOpenTypeConverter.cachedType(qualifiedName);
            } catch (OpenDataException ignore) {
            }

            if (rawValue instanceof Number && attributeType != null) {
                return new JolokiaSerializer()
                    .deserializeOpenType(attributeType, rawValue);
            } else {
                return rawValue;
            }
        }

        // special case, if the attribute is ObjectName
        if (rawValue instanceof JSONObject
            && ((JSONObject) rawValue).size() == 1
            && ((JSONObject) rawValue).containsKey("objectName")) {
            return getObjectName("" + ((JSONObject) rawValue).get("objectName"));
        }
        try {
            return ToOpenTypeConverter.returnOpenTypedValue(qualifiedName, rawValue, typeFromMBeanInfo);
        } catch (OpenDataException e) {
            return rawValue;
        }
    }

    private boolean isPrimitive(Object rawValue) {
        return rawValue == null
            || rawValue instanceof Number
            || rawValue instanceof Boolean
            || rawValue instanceof String
            || rawValue instanceof Character;
    }

    static ObjectName getObjectName(String objectName) {
        try {
            return ObjectName.getInstance(objectName);
        } catch (MalformedObjectNameException e) {
            throw new UncheckedJmxAdapterException(e);
        }
    }

    @Override
    public AttributeList getAttributes(ObjectName name, String[] attributes)
        throws InstanceNotFoundException, IOException {
        AttributeList result = new AttributeList();

        List<JolokiaReadRequest> requests = new ArrayList<>(attributes.length);

        for (String attribute : attributes) {
            requests.add(new JolokiaReadRequest(name, attribute));
        }
        List<?> responses = Collections.emptyList();
        try {
            responses = this.connector.execute(requests, this.defaultProcessingOptions());

        } catch (JolokiaBulkRemoteException e) {
            responses = e.getResults();
        } catch (JolokiaException ignore) {
            //will result in empty return
        }
        for (Object item : responses) {
            if (item instanceof JolokiaReadResponse value) {
                final String attribute = value.getRequest().getAttribute();
                result.add(new Attribute(attribute,
                    adaptJsonToOptimalResponseValue(name, attribute, value.getValue(), getAttributeTypeFromMBeanInfo(name, attribute))));
            }
        }
        return result;
    }

    @Override
    public void setAttribute(ObjectName name, Attribute attribute)
        throws InstanceNotFoundException, AttributeNotFoundException, InvalidAttributeValueException,
        IOException {
        final JolokiaWriteRequest request =
            new JolokiaWriteRequest(name, attribute.getName(), attribute.getValue());
        try {
            this.unwrapExecute(request);
        } catch (UncheckedJmxAdapterException e) {
            if (e.getCause() instanceof JolokiaRemoteException remote) {
                if ("javax.management.AttributeNotFoundException".equals(remote.getErrorType())) {
                    throw new AttributeNotFoundException((e.getCause().getMessage()));
                }
                if ("java.lang.IllegalArgumentException".equals(remote.getErrorType())
                    && remote
                    .getMessage()
                    .matches(
                        "Error: java.lang.IllegalArgumentException : Invalid value .+ for attribute .+")) {
                    throw new InvalidAttributeValueException(remote.getMessage());
                }
            }
            throw e;
        }
    }

    @Override
    public AttributeList setAttributes(ObjectName name, AttributeList attributes)
        throws InstanceNotFoundException, IOException {

        List<JolokiaWriteRequest> attributeWrites = new ArrayList<>(attributes.size());
        for (Attribute attribute : attributes.asList()) {
            attributeWrites.add(new JolokiaWriteRequest(name, attribute.getName(), attribute.getValue()));
        }
        try {
            this.connector.execute(attributeWrites);
        } catch (JolokiaException e) {
            unwrapException(e);
        }

        return attributes;
    }

    @Override
    public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature)
        throws InstanceNotFoundException, MBeanException, IOException {
        // JVisualVM may send null for no parameters
        if (params == null && (signature == null || signature.length == 0)) {
            params = new Object[0];
        }
        try {
            final JolokiaExecResponse response =
                unwrapExecute(
                    new JolokiaExecRequest(name, operationName + makeSignature(signature), params));
            return adaptJsonToOptimalResponseValue(name, operationName, response.getValue(), getOperationTypeFromMBeanInfo(name, operationName, signature));
        } catch (UncheckedJmxAdapterException e) {
            if (e.getCause() instanceof JolokiaRemoteException) {
                throw new MBeanException((Exception) e.getCause());
            }
            throw e;
        }
    }

    private String getOperationTypeFromMBeanInfo(ObjectName name, String operationName, String[] signature)
        throws IOException, InstanceNotFoundException {
        final MBeanInfo mBeanInfo = getMBeanInfo(name);
        if (signature == null) {
            signature = new String[0];
        }
        for (final MBeanOperationInfo operation : mBeanInfo.getOperations()) {
            if (operationName.equals(operation.getName()) &&
                Arrays.equals(
                    Arrays.stream(operation.getSignature()).map(MBeanParameterInfo::getType).toArray(String[]::new),
                    signature)) {
                return operation.getReturnType();
            }
        }
        return null;
    }

    private String makeSignature(String[] signature) {
        StringBuilder builder = new StringBuilder("(");
        if (signature != null) {
            for (int i = 0; i < signature.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(signature[i]);
            }
        }
        builder.append(')');
        return builder.toString();
    }

    @Override
    public String getDefaultDomain() {
        return "DefaultDomain";
    }

    @Override
    public String[] getDomains() throws IOException {
        Set<String> domains = new HashSet<>();
        for (final ObjectName name : this.queryNames(null, null)) {
            domains.add(name.getDomain());
        }
        return domains.toArray(new String[0]);
    }

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

    private boolean isRunningInJVisualVm() {
        final String version = System.getProperty("netbeans.productversion");
        return version != null && version.contains("VisualVM");
    }

    private boolean isRunningInJConsole() {
        return System.getProperty("jconsole.showOutputViewer") != null;
    }

    private boolean isRunningInJmc() {
        return System.getProperty("running.in.jmc") != null;
    }

    @Override
    public MBeanInfo getMBeanInfo(ObjectName name) throws InstanceNotFoundException, IOException {
        MBeanInfo result = this.mbeanInfoCache.get(name);
        //cache in case client queries a lot for MBean info
        if (result == null) {
            final JolokiaListResponse response = this.unwrapExecute(new JolokiaListRequest(name));
            result = response.getMbeanInfo(name);
            if (result != null) {
                this.mbeanInfoCache.put(name, result);
                for (MBeanAttributeInfo attr : result.getAttributes()) {
                    final String qualifiedName = name + "." + attr.getName();
                    try {
                        if (ToOpenTypeConverter.cachedType(qualifiedName) == null) {
                            ToOpenTypeConverter
                                .cacheType(ToOpenTypeConverter.typeFor(attr.getType()), qualifiedName);
                        }
                    } catch (OpenDataException | InvalidOpenTypeException ignore) {
                    }
                }
            }
        }
        return result;
    }

    private String getAttributeTypeFromMBeanInfo(final ObjectName name, final String attributeName)
        throws IOException, InstanceNotFoundException {
        final MBeanInfo mBeanInfo = getMBeanInfo(name);
        for (final MBeanAttributeInfo attribute : mBeanInfo.getAttributes()) {
            if (attributeName.equals(attribute.getName())) {
                return attribute.getType();
            }
        }
        return null;
    }

    @Override
    public boolean isInstanceOf(ObjectName name, String className) throws InstanceNotFoundException, IOException {
        // the algorithm from the JavaDoc mentions:
        // > let:
        // > X be the MBean named by name
        // > L be the ClassLoader of X
        // there's no way to get the "L" even if we get a full JSON representation of MBeanInfo using
        // Jolokia's list() operation. So we can't do any classloading.
        JolokiaListResponse listResponse = this.unwrapExecute(new JolokiaListRequest(name), () -> {
            Map<JolokiaQueryParameter, String> options = defaultProcessingOptions();
            // handled by Jolokia 2.5.0+
            options.put(JolokiaQueryParameter.LIST_INTERFACES, "true");
            return options;
        });
        JSONObject info = listResponse.getJSONMbeanInfo(name);
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
            return interfaces.contains(className);
        } else {
            // this method may be used by jconsole when crating proxies for platform MBeans. So we can
            // use some hardcoded information
            Set<String> knownInterfaces = platformMBeanInterfaces.get(name);
            if (knownInterfaces != null) {
                return knownInterfaces.contains(className);
            } else {
                if (platformGarbageCollectorMBeanPattern.apply(name)) {
                    // special pattern matching for GarbageCollector MBean
                    return GarbageCollectorMXBean.class.getName().equals(className);
                }
                // the only thing we can do is direct check
                return className.equals(mbeanClassName);
            }
        }
    }

    String getId() {
        return this.agentId;
    }

}
