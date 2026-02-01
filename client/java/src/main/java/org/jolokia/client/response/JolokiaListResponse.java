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
package org.jolokia.client.response;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import javax.management.Descriptor;
import javax.management.ImmutableDescriptor;
import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.RuntimeOperationsException;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenMBeanAttributeInfoSupport;
import javax.management.openmbean.OpenMBeanConstructorInfoSupport;
import javax.management.openmbean.OpenMBeanOperationInfoSupport;
import javax.management.openmbean.OpenMBeanParameterInfo;
import javax.management.openmbean.OpenMBeanParameterInfoSupport;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

import org.jolokia.client.request.JolokiaListRequest;
import org.jolokia.converter.object.ObjectToOpenTypeConverter;
import org.jolokia.converter.object.OpenTypeHelper;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;

/**
 * <p>Response for a {@link JolokiaListRequest}. Full response (when not using {@code maxDepth} parameter) contains
 * a complex map:<ul>
 *     <li>Top level keys are {@link ObjectName#getDomain()}</li>
 *     <li>2nd level is for {@link ObjectName#getCanonicalKeyPropertyListString()}</li>
 *     <li>Deeper levels are for JSON representation of {@link MBeanInfo}, but additional data may be available
 *     if Data Updaters are used at server side.</li>
 * </ul>
 * </p>
 *
 * <p>In addition, an optimized list response may be provided, where top-level elements are "cache" and "domains". This
 * representation can be used with {@code listCache=true} processing parameter.</p>
 *
 * <p>After https://github.com/jolokia/jolokia/issues/894 where list response is fixed, we've catalogued several kinds
 * of responses. These depend on the {@code path} argument to list operation - it may be in these forms:<ul>
 *     <li>{@code /mbean-domain-or-pattern}</li>
 *     <li>{@code /mbean-domain-or-pattern/keys-list-or-pattern}</li>
 *     <li>{@code /mbean-domain-or-pattern/keys-list-or-pattern/updater-to-use}</li>
 * </ul>
 * If {@link ObjectName#isPattern()} is used, the response may include more domains or object names within a domain.
 * So it's not possible to <em>drill into</em> the response. Without a pattern though, the top level elements
 * may be the object name or just the content of single updater</p>
 *
 * @author roland
 * @since 26.03.11
 */
public final class JolokiaListResponse extends JolokiaResponse<JolokiaListRequest> {

    // possible responses depending on the path ("updater" is something like "class" or "attr" - generally
    // a key to JSON representation of MBeanInfo, but also a key of custom DataUpdater):
    //
    // 1. no patterns/wildcards
    // 1.1 /list
    //     {
    //       "request": {
    //         "type": "list"
    //       },
    //       "value": {
    //         "jdk.management.jfr": {
    //           "type=FlightRecorder": {
    //             "op": {
    //               "getRecordingOptions": ...,
    //               ...
    // 1.2 /list/domain
    //     {
    //       "request": {
    //         "path": "jdk.management.jfr",
    //         "type": "list"
    //       },
    //       "value": {
    //         "type=FlightRecorder": {
    //           "op": {
    //             "getRecordingOptions": {
    //               "args": [
    //                 ...
    // 1.3 /list/domain/key-list
    //     {
    //       "request": {
    //         "path": "jdk.management.jfr/type=FlightRecorder",
    //         "type": "list"
    //       },
    //       "value": {
    //         "op": {
    //           "getRecordingOptions": {
    //             "args": [
    //               {
    //                 "name": "p0",
    //                 ...
    // 1.4 /list/domain/key-list/updater
    //     {
    //       "request": {
    //         "path": "jdk.management.jfr/type=FlightRecorder/op",
    //         "type": "list"
    //       },
    //       "value": {
    //         "getRecordingOptions": {
    //           "args": [
    //             {
    //               "name": "p0",
    //               "type": "long",
    //
    // 2. with patterns/wildcards
    // 2.1 /list/domain-pattern - full version, same as 1.1
    //     {
    //       "request": {
    //         "path": "jdk.management.jf*",
    //         "type": "list"
    //       },
    //       "value": {
    //         "jdk.management.jfr": {
    //           "type=FlightRecorder": {
    //             "op": {
    //               "getRecordingOptions": ...,
    //               ...
    // 2.2 /list/domain-pattern/key-list - same as 2.1
    //     {
    //       "request": {
    //         "path": "jdk.management.jf*/type=FlightRecorder",
    //         "type": "list"
    //       },
    //       "value": {
    //         "jdk.management.jfr": {
    //           "type=FlightRecorder": {
    //             "op": {
    //               "getRecordingOptions": ...,
    //               ...
    // 2.3 /list/domain-pattern/key-list-pattern - same as 2.1 and 2.2
    //     {
    //       "request": {
    //         "path": "jdk.management.jf*/type=*",
    //         "type": "list"
    //       },
    //       "value": {
    //         "jdk.management.jfr": {
    //           "type=FlightRecorder": {
    //             "op": {
    //               "getRecordingOptions": 1,
    // 2.4 /list/domain/key-list-pattern - same as 1.2 - shallow by trimming single top-level domain
    //     {
    //       "request": {
    //         "path": "jdk.management.jfr/type=*",
    //         "type": "list"
    //       },
    //       "value": {
    //         "type=FlightRecorder": {
    //           "op": {
    //             "getRecordingOptions": {
    //               "args": [
    // 2.5 /list/domain-pattern/key-list/updater - full version, same as 1.1
    //     {
    //       "request": {
    //         "path": "jdk.management.*/type=FlightRecorder/class",
    //         "type": "list"
    //       },
    //       "value": {
    //         "jdk.management.jfr": {
    //           "type=FlightRecorder": {
    //             "class": "jdk.management.jfr.FlightRecorderMXBeanImpl"
    //           }
    //         }
    //       },
    // 2.6 /list/domain-pattern/key-list-pattern/updater - full version, same as 1.1
    //     {
    //       "request": {
    //         "path": "jdk.management.*/type=FlightRecorder/class",
    //         "type": "list"
    //       },
    //       "value": {
    //         "jdk.management.jfr": {
    //           "type=FlightRecorder": {
    //             "class": "jdk.management.jfr.FlightRecorderMXBeanImpl"
    //           }
    //         }
    //       },
    // 2.7 /list/domain/key-list-pattern/updater - same as 1.2 - shallow by trimming single top-level domain
    //     {
    //       "request": {
    //         "path": "jdk.management.jfr/type=*/class",
    //         "type": "list"
    //       },
    //       "value": {
    //         "type=FlightRecorder": {
    //           "class": "jdk.management.jfr.FlightRecorderMXBeanImpl"
    //         }
    //       },
    //
    // 3. no patterns/wildcards, listCache=true
    // 3.1 /list
    //     cache + domains for all MBeans
    // 3.2 /list/domain
    //     cache + domains for all MBeans within a domain, no trimming (different than 1.2)
    // 3.3 /list/domain/key-list
    //     cache disabled, because only one matching MBean. same as 1.3, trim by 2 levels
    // 3.4 /list/domain/key-list/updater
    //     cache disabled, because only one matching MBean. same as 1.4, trim by 3 levels
    //
    // 4. with patterns/wildcards, listCache=true (never trim)
    // 4.1 /list/domain-pattern - cache, full version, same as 1.1, matching domains
    // 4.2 /list/domain-pattern/key-list - cache, full version, same as 2.1, matching domain (because single MBean)
    // 4.3 /list/domain-pattern/key-list-pattern - cache, full version, matching domains and MBeans
    // 4.4 /list/domain/key-list-pattern - cache, full version, matching MBeans for single domain
    // 4.5 /list/domain-pattern/key-list/updater - same as 4.2, but single updater used
    // 4.6 /list/domain-pattern/key-list-pattern/updater - same as 4.3, but single updater used
    // 4.7 /list/domain/key-list-pattern/updater - same as 4.6, but single domain
    //
    // Summarizing:
    //  - without patterns, each (of 3 possible) path element drills in by 1 level
    //  - with pattern, each left non-pattern path element drills in by 1 level - up to first pattern path element

    // fields similar to org.jolokia.service.jmx.handler.list.MBeanInfoData

    /**
     * ObjectName or pattern recreated from 1st and 2nd path element.
     */
    private ObjectName pathObjectName;
    private String pathDomain;
    private String pathKeys;

    /**
     * 3rd path segment can be used to select single {@code DataUpdater}. No more path segments are supported.
     */
    private String selectedUpdater;

    /** Whether the response is in the optimized form */
    private final boolean useCache;

    /**
     * Create a response to {@link org.jolokia.client.JolokiaOperation#LIST} based on {@link JSONObject} representing
     * various possible forms of list response.
     *
     * @param pRequest
     * @param pJsonResponse
     */
    public JolokiaListResponse(JolokiaListRequest pRequest, JSONObject pJsonResponse) {
        super(pRequest, pJsonResponse);

        // restructure the response based on the request - see above
        JSONObject value = getValue();
        // cache was turned on by sending listCache=true processing parameter, but we can find this out by
        // checking the response structure
        useCache = value.size() == 2 && value.containsKey("cache") && value.containsKey("domains");
        rebuildResponse();
    }

    /**
     * Based on initial {@link JolokiaListRequest} we should configure hints about what to expect in the response.
     */
    private void determineNesting() {
        JolokiaListRequest req = getRequest();
        Deque<String> pathElements = new LinkedList<>(req.getRequestParts());
        if (pathElements.isEmpty()) {
            // all domains/mbeans
            pathObjectName = null;
            selectedUpdater = null;
        } else {
            String domain = pathElements.pop();
            if (domain == null) {
                domain = "*";
            } else {
                pathDomain = domain;
            }
            String name = "*";
            if (!pathElements.isEmpty()) {
                name = pathElements.pop();
                if (name == null) {
                    name = "*";
                } else {
                    pathKeys = name;
                }
            }
            try {
                pathObjectName = "*".equals(domain) && "*".equals(name) ? null : new ObjectName(domain + ":" + name);
            } catch (MalformedObjectNameException e) {
                throw new IllegalArgumentException("The response uses illegal ObjectPattern path: "
                    + String.join("/", req.getRequestParts()));
            }
            if (!pathElements.isEmpty()) {
                selectedUpdater = pathElements.pop();
            }
        }
    }

    /**
     * After calling this method, the response will be changed to always contain domain-mbean-updater-info mapping.
     */
    private void rebuildResponse() {
        determineNesting();

        JSONObject newValue = new JSONObject();
        if (useCache) {
            // replace cache keys with actual JSON representation of full/partial MBeanInfo
            JSONObject oldValue = getValue(JSONObject.class);
            JSONObject cache = (JSONObject) oldValue.get("cache");
            JSONObject domains = (JSONObject) oldValue.get("domains");
            newValue.putAll(domains);
            for (Map.Entry<String, ?> e : domains.entrySet()) {
                if (e.getValue() instanceof JSONObject mbeans) {
                    // we have a map of mbean-keys into 1) its MBeanInfo OR 2) cache key
                    for (Map.Entry<String, ?> e2 : mbeans.entrySet()) {
                        if (e2.getValue() instanceof String cacheKey) {
                            Object cachedInfo = cache.get(cacheKey);
                            if (cachedInfo != null) {
                                // MBeanInfo is cached, so replace key with the cached value - by reference, saving memory
                                ((JSONObject) newValue.get(e.getKey())).put(e2.getKey(), cachedInfo);
                            }
                        }
                    }
                }
            }
            oldValue.remove("cache");
            oldValue.remove("domains");
            asJSONObject().put("value", newValue);
        } else {
            // set up proper domain -> mbean -> updater -> MBeanInfo hierarchy, because the response may
            // container trimmed (from top) information
            // "pathKeys" is from the objectName sent with JolokiaListRequest and it's not important
            // what value for org.jolokia.client.J4pQueryParameter.CANONICAL_NAMING we've sent originally
            if (pathObjectName != null && !pathObjectName.isPattern() && selectedUpdater != null) {
                // response #1.4 - 3 level nesting, pathDomain and pathKeys are specified
                JSONObject domain = (JSONObject) newValue.computeIfAbsent(pathDomain, k -> new JSONObject());
                JSONObject mbean = (JSONObject) domain.computeIfAbsent(pathKeys, k -> new JSONObject());
                mbean.put(selectedUpdater, getValue());
                asJSONObject().put("value", newValue);
            } else if (pathObjectName != null && !pathObjectName.isPattern() && selectedUpdater == null) {
                // response #1.3 - 2 level nesting, pathDomain and pathKeys are specified
                JSONObject domain = (JSONObject) newValue.computeIfAbsent(pathDomain, k -> new JSONObject());
                JSONObject mbean = (JSONObject) domain.computeIfAbsent(pathKeys, k -> getValue());
                asJSONObject().put("value", newValue);
            } else if (pathObjectName != null && !pathObjectName.isDomainPattern() && selectedUpdater == null) {
                // response #1.2 - 1 level nesting, pathDomain is specified
                JSONObject domain = (JSONObject) newValue.computeIfAbsent(pathDomain, k -> getValue());
                asJSONObject().put("value", domain);
            }
        }
    }

    // Methods that can be used to retrieve some information from various forms of "list" response. These
    // are used to implement these MBeanServerConnection methods in jolokia-client-jmx-adapter
    //  -  javax.management.MBeanServerConnection.getObjectInstance()
    //  -  javax.management.MBeanServerConnection.getMBeanInfo()
    //  -  javax.management.MBeanServerConnection.queryMBeans() ("search" is used for queryNames())

    /**
     * Helper for {@link MBeanServerConnection#queryMBeans(ObjectName, QueryExp)} where we may want to retrieve
     * matching {@link ObjectInstance object instances} based on {@link ObjectName object name pattern}.
     *
     * @param name {@link ObjectName} passed as {@link org.jolokia.client.JolokiaOperation#LIST} argument. May
     *             be a pattern. The response contains all matching MBeans, so no need to filter the response again
     * @return
     */
    public List<ObjectInstance> getObjectInstances(ObjectName name) {
        List<ObjectInstance> result = new LinkedList<>();

        // already recombined return value
        JSONObject domains = this.getValue();
        for (Entry<String, Object> e1 : domains.entrySet()) {
            String domain = e1.getKey();
            if (e1.getValue() instanceof JSONObject mbeans) {
                for (Entry<String, Object> e2: mbeans.entrySet()) {
                    String mbean = e2.getKey();
                    if (e2.getValue() instanceof JSONObject data && data.get("class") instanceof String cls) {
                        try {
                            result.add(new ObjectInstance(new ObjectName(domain + ":" + mbean), cls));
                        } catch (MalformedObjectNameException e) {
                            // should come from valid list response, so change into a runtime exception
                            throw new IllegalStateException("Cannot convert list result '" + name + "' to an ObjectName", e);
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Helper for {@link MBeanServerConnection#getObjectInstance(ObjectName)}. We expect no
     * {@link ObjectName#isPattern() pattern here}. As in
     * {@code com.sun.jmx.mbeanserver.Repository#retrieveNamedObject()} we return {@code null} if the argument
     * is a pattern
     *
     * @return
     */
    public String getClassName(ObjectName name) throws InstanceNotFoundException {
        if (name == null) {
            // com.sun.jmx.interceptor.DefaultMBeanServerInterceptor.getMBean throws an exception
            throw new RuntimeOperationsException(new IllegalArgumentException("Object name can't be null"));
        }
        if (name.isPattern()) {
            return null;
        }

        JSONObject domains = this.getValue();
        Object domain = domains.get(name.getDomain());
        if (domain instanceof JSONObject mbeans) {
            Object mbean = mbeans.get(pathKeys);
            if (mbean instanceof JSONObject info) {
                return info.get("class") instanceof String s ? s : "<unknown>";
            } else if (mbean == null && mbeans.size() == 1) {
                // we don't have access to org.jolokia.client.J4pQueryParameter.CANONICAL_NAMING value
                // sent with JolokiaListRequest here, but we should have only a single MBean here
                mbean = mbeans.values().iterator().next();
                if (mbean instanceof JSONObject info) {
                    return info.get("class") instanceof String s ? s : "<unknown>";
                }
            }
        }

        throw new InstanceNotFoundException("No " + name + " instance found");
    }

    /**
     * Helper for {@link MBeanServerConnection#getMBeanInfo(ObjectName)}. We expect no
     * {@link ObjectName#isPattern() pattern} here. As in
     * {@code com.sun.jmx.mbeanserver.Repository#retrieveNamedObject()} we return {@code null} if the argument
     * is a pattern.
     *
     * @param name
     * @return
     * @throws InstanceNotFoundException
     */
    public MBeanInfo getMBeanInfo(ObjectName name) throws InstanceNotFoundException {
        if (name == null) {
            // com.sun.jmx.interceptor.DefaultMBeanServerInterceptor.getMBean throws an exception
            throw new RuntimeOperationsException(new IllegalArgumentException("Object name can't be null"));
        }
        if (name.isPattern()) {
            return null;
        }

        JSONObject domains = this.getValue();
        Object domain = domains.get(name.getDomain());
        if (domain instanceof JSONObject mbeans) {
            Object mbean = mbeans.get(pathKeys);
            if (mbean instanceof JSONObject info) {
                return mBeanInfoFrom(name, info);
            } else if (mbean == null && mbeans.size() == 1) {
                // we don't have access to org.jolokia.client.J4pQueryParameter.CANONICAL_NAMING value
                // sent with JolokiaListRequest here, but we should have only a single MBean here
                mbean = mbeans.values().iterator().next();
                if (mbean instanceof JSONObject info) {
                    return mBeanInfoFrom(name, info);
                }
            }
        }

        throw new InstanceNotFoundException("No " + name + " instance found");
    }

    /**
     * Helper for {@link MBeanServerConnection#getMBeanInfo(ObjectName)} returning plain JSON data. We expect no
     * {@link ObjectName#isPattern() pattern} here. As in
     * {@code com.sun.jmx.mbeanserver.Repository#retrieveNamedObject()} we return {@code null} if the argument
     * is a pattern.
     *
     * @param name
     * @return
     * @throws InstanceNotFoundException
     */
    public JSONObject getJSONMBeanInfo(ObjectName name) throws InstanceNotFoundException {
        if (name == null) {
            // com.sun.jmx.interceptor.DefaultMBeanServerInterceptor.getMBean throws an exception
            throw new RuntimeOperationsException(new IllegalArgumentException("Object name can't be null"));
        }
        if (name.isPattern()) {
            return null;
        }

        JSONObject domains = this.getValue();
        Object domain = domains.get(name.getDomain());
        if (domain instanceof JSONObject mbeans) {
            Object mbean = mbeans.get(pathKeys);
            if (mbean instanceof JSONObject info) {
                return info;
            } else if (mbean == null && mbeans.size() == 1) {
                // we don't have access to org.jolokia.client.J4pQueryParameter.CANONICAL_NAMING value
                // sent with JolokiaListRequest here, but we should have only a single MBean here
                mbean = mbeans.values().iterator().next();
                if (mbean instanceof JSONObject info) {
                    return info;
                }
            }
        }

        throw new InstanceNotFoundException("No " + name + " instance found");
    }

    /**
     * Reconstruct {@link MBeanInfo} from its Jolokia {@link JSONObject} representation.
     *
     * @param name
     * @param info
     * @return
     */
    private MBeanInfo mBeanInfoFrom(ObjectName name, JSONObject info) {
        Object classV = info.get("class");
        String className;
        Object descriptionV = info.get("desc");
        String description;

        Object attributesV = info.get("attr");
        MBeanAttributeInfo[] attributes;
        Object constructorsV = info.get("ctor");
        MBeanConstructorInfo[] constructors;
        Object operationsV = info.get("op");
        MBeanOperationInfo[] operations;
        Object notificationsV = info.get("notif");
        MBeanNotificationInfo[] notifications;

        if (classV == null || classV instanceof String) {
            className = (String) classV;
        } else {
            throw new IllegalStateException("Classname from MBeanInfo of " + name + " is invalid: " + classV);
        }
        if (descriptionV == null || descriptionV instanceof String) {
            description = (String) descriptionV;
        } else {
            throw new IllegalStateException("Description from MBeanInfo of " + name + " is invalid: " + descriptionV);
        }

        if (attributesV == null || attributesV instanceof JSONObject) {
            attributes = attributesFrom((JSONObject) attributesV);
        } else {
            throw new IllegalStateException("Attributes from MBeanInfo of " + name + " are invalid: " + attributesV);
        }
        if (operationsV == null || operationsV instanceof JSONObject) {
            operations = operationsFrom((JSONObject) operationsV);
        } else {
            throw new IllegalStateException("Operations from MBeanInfo of " + name + " are invalid: " + operationsV);
        }
        if (constructorsV == null || constructorsV instanceof JSONObject) {
            constructors = constructorsFrom((JSONObject) constructorsV);
        } else {
            throw new IllegalStateException("Constructors from MBeanInfo of " + name + " are invalid: " + constructorsV);
        }
        if (notificationsV == null || notificationsV instanceof JSONObject) {
            notifications = notificationsFrom((JSONObject) notificationsV);
        } else {
            throw new IllegalStateException("Notifications from MBeanInfo of " + name + " are invalid: " + notificationsV);
        }

        return new MBeanInfo(className, description, attributes, constructors, operations, notifications);
    }

    /**
     * Convert Jolokia representation of attributes from {@link MBeanInfo} to an array of {@link MBeanAttributeInfo}
     *
     * @param attributes
     * @return
     */
    private MBeanAttributeInfo[] attributesFrom(JSONObject attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return new MBeanAttributeInfo[0];
        }
        // sort alphabetically to match native MBeanServer
        Map<String, Object> sorted = new TreeMap<>(attributes);
        final MBeanAttributeInfo[] result = new MBeanAttributeInfo[sorted.size()];

        int index = 0;
        for (Map.Entry<String, Object> attribute : sorted.entrySet()) {
            if (!(attribute.getValue() instanceof JSONObject)) {
                throw new IllegalArgumentException("Representation of \"" + attribute.getKey() + "\" is invalid: " + attribute.getValue());
            }
            result[index++] = attributeFrom(attribute.getKey(), (JSONObject) attribute.getValue());
        }
        return result;
    }

    /**
     * Convert Jolokia representation of single attribute from {@link MBeanInfo} to {@link MBeanAttributeInfo}
     *
     * @param name
     * @param json
     * @return
     */
    private MBeanAttributeInfo attributeFrom(String name, JSONObject json) {
        String type = json.get("type") instanceof String typeV ? typeV : "<unknown>";
        String desc = json.get("desc") instanceof String descV ? descV : "";
        boolean r = json.get("r") instanceof Boolean rV ? rV : false;
        boolean w = json.get("w") instanceof Boolean wV ? wV : false;
        boolean rw = json.get("rw") instanceof Boolean rwV ? rwV : false;
        boolean is = json.get("is") instanceof Boolean isV ? isV : false;

        // new in Jolokia 2.5.0 - OpenType support
        try {
            OpenType<?> openType = OpenTypeHelper.fromJSON(json.get("openType"));
            if (openType != null) {
                // com.sun.jmx.mbeanserver.MXBeanIntrospector.canUseOpenInfo() is used for attributes/operations
                // and according to JSR 174, primitive types NEVER use javax.management.openmbean.OpenMBeanAttributeInfo even
                // if they could. The only connection to open type is in javax.management.MBeanFeatureInfo.descriptor which
                // contains "openType" field.
                Descriptor descriptor = new ImmutableDescriptor(Map.of(
                    JMX.OPEN_TYPE_FIELD, openType
                ));
                if (SimpleType.class.isAssignableFrom(openType.getClass())) {
                    SimpleType<?> simpleType = ObjectToOpenTypeConverter.knownPrimitiveOpenType(type);
                    if (simpleType != null) {
                        // this is really a primitive type
                        return new MBeanAttributeInfo(name, type, desc, r, w, is, descriptor);
                    } else {
                        // this should be a wrapped type, so can be open attribute info
                        return new OpenMBeanAttributeInfoSupport(name, desc, openType, r, w, is, descriptor);
                    }
                } else {
                    return new OpenMBeanAttributeInfoSupport(name, desc, openType, r, w, is, descriptor);
                }
            }
        } catch (OpenDataException ignored) {
        }

        // for example we can know only that the type is CompositeData, but we can't know the structure
        // without seeing an example data - and that's how jolokia-client-jmx-adapter worked before
        // list operation started returning actual OpenType information
        return new MBeanAttributeInfo(name, type, desc, r, w, is);
    }

    /**
     * Convert Jolokia representation of operations from {@link MBeanInfo} to an array of {@link MBeanOperationInfo}
     * @param operations
     * @return
     */
    private MBeanOperationInfo[] operationsFrom(JSONObject operations) {
        if (operations == null || operations.isEmpty()) {
            return new MBeanOperationInfo[0];
        }
        final List<MBeanOperationInfo> result = new ArrayList<>(operations.size());

        for (Map.Entry<String, Object> operation : operations.entrySet()) {
            // if more operations with same name (overloaded), the value part is an array
            if (operation.getValue() instanceof JSONArray overloaded) {
                for (Object operationItem : overloaded) {
                    if (operationItem instanceof JSONObject item) {
                        result.add(operationFrom(operation.getKey(), item));
                    } else {
                        throw new IllegalArgumentException("Representation of overloaded operation \""
                            + operation.getKey() + "\" is invalid: " + operationItem);
                    }
                }
            } else if (operation.getValue() instanceof JSONObject op) {
                result.add(operationFrom(operation.getKey(), op));
            } else {
                throw new IllegalArgumentException("Representation of operation \""
                    + operation.getKey() + "\" is invalid: " + operation.getValue());
            }
        }
        return result.toArray(new MBeanOperationInfo[0]);
    }

    /**
     * Convert Jolokia representation of single operation from {@link MBeanInfo} to {@link MBeanOperationInfo}
     *
     * @param name
     * @param json
     * @return
     */
    private MBeanOperationInfo operationFrom(String name, JSONObject json) {
        String desc = json.get("desc") instanceof String descV ? descV : "";
        String type = json.get("ret") instanceof String typeV ? typeV : "<unknown>";
        Object parametersV = json.get("args");
        Object parameters;

        if (parametersV instanceof JSONArray pJson) {
            parameters = parametersFrom(name, pJson);
        } else {
            throw new IllegalStateException("Parameters from operation " + name + " are invalid: " + parametersV);
        }

        // new in Jolokia 2.5.0 - OpenType support
        try {
            OpenType<?> openRetType = OpenTypeHelper.fromJSON(json.get("openRet"));
            if (openRetType != null) {
                if (parameters instanceof OpenMBeanParameterInfo[] openParams) {
                    return new OpenMBeanOperationInfoSupport(name, desc, openParams, openRetType, MBeanOperationInfo.UNKNOWN);
                } else {
                    // normal parameters, but we can still sneak in the OpenType return information
                    MBeanParameterInfo[] normalParams = (MBeanParameterInfo[]) parameters;
                    Descriptor descriptor = new ImmutableDescriptor(Map.of(
                        JMX.OPEN_TYPE_FIELD, openRetType
                    ));
                    return new MBeanOperationInfo(name, desc, normalParams, type, MBeanOperationInfo.UNKNOWN, descriptor);
                }
            }
        } catch (OpenDataException ignored) {
        }

        return new MBeanOperationInfo(name, desc, (MBeanParameterInfo[]) parameters, type, MBeanOperationInfo.UNKNOWN);
    }

    /**
     * Convert Jolokia representation of parameters from {@link MBeanOperationInfo} to an array of
     * {@link MBeanParameterInfo}
     *
     * @param operation
     * @param args
     * @return
     */
    private Object parametersFrom(String operation, JSONArray args) {
        final List<MBeanParameterInfo> params = new ArrayList<>(args.size());
        boolean allOpenTypeParams = !args.isEmpty();
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i) instanceof JSONObject arg) {
                MBeanParameterInfo paramInfo = parameterFrom(i, arg);
                params.add(paramInfo);
                allOpenTypeParams &= paramInfo instanceof OpenMBeanParameterInfo;
            } else {
                throw new IllegalStateException("Parameter " + i + " from operation " + operation + " is invalid: " + args.get(i));
            }
        }

        if (allOpenTypeParams) {
            return params.stream().map(OpenMBeanParameterInfo.class::cast).toArray(OpenMBeanParameterInfo[]::new);
        } else {
            return params.toArray(MBeanParameterInfo[]::new);
        }
    }

    /**
     * Convert Jolokia representation of single parameter from {@link MBeanOperationInfo} to {@link MBeanParameterInfo}
     *
     * @param pn
     * @param parameter
     * @return
     */
    private MBeanParameterInfo parameterFrom(int pn, JSONObject parameter) {
        String name = parameter.get("name") instanceof String nameV ? nameV : "p" + pn;
        String type = parameter.get("type") instanceof String typeV ? typeV : "<unknown>";
        String desc = parameter.get("desc") instanceof String descV ? descV : "p" + pn;

        // new in Jolokia 2.5.0 - OpenType support
        try {
            OpenType<?> openType = OpenTypeHelper.fromJSON(parameter.get("openType"));
            if (openType != null) {
                Descriptor descriptor = new ImmutableDescriptor(Map.of(
                    JMX.OPEN_TYPE_FIELD, openType
                ));

                if (SimpleType.class.isAssignableFrom(openType.getClass())) {
                    SimpleType<?> simpleType = ObjectToOpenTypeConverter.knownPrimitiveOpenType(type);
                    if (simpleType != null) {
                        // this is really a primitive type
                        return new MBeanParameterInfo(name, type, desc, descriptor);
                    } else {
                        // this should be a wrapped type, so can be open attribute info
                        return new OpenMBeanParameterInfoSupport(name, desc, openType, descriptor);
                    }
                } else {
                    return new OpenMBeanParameterInfoSupport(name, desc, openType, descriptor);
                }
            }
        } catch (OpenDataException ignored) {
        }

        return new MBeanParameterInfo(name, type, desc);
    }

    /**
     * Convert Jolokia representation of constructors from {@link MBeanInfo} to an array of {@link MBeanConstructorInfo}
     * @param constructors
     * @return
     */
    private MBeanConstructorInfo[] constructorsFrom(JSONObject constructors) {
        if (constructors == null || constructors.isEmpty()) {
            return new MBeanConstructorInfo[0];
        }
        final List<MBeanConstructorInfo> result = new ArrayList<>(constructors.size());

        for (Map.Entry<String, Object> constructor : constructors.entrySet()) {
            // if more operations with same name (overloaded), the value part is an array
            if (constructor.getValue() instanceof JSONArray overloaded) {
                for (Object constructorItem : overloaded) {
                    if (constructorItem instanceof JSONObject item) {
                        result.add(constructorFrom(constructor.getKey(), item));
                    } else {
                        throw new IllegalArgumentException("Representation of overloaded constructor \""
                            + constructor.getKey() + "\" is invalid: " + constructorItem);
                    }
                }
            } else if (constructor.getValue() instanceof JSONObject op) {
                result.add(constructorFrom(constructor.getKey(), op));
            } else {
                throw new IllegalArgumentException("Representation of constructor \""
                    + constructor.getKey() + "\" is invalid: " + constructor.getValue());
            }
        }
        return result.toArray(new MBeanConstructorInfo[0]);
    }

    /**
     * Convert Jolokia representation of single constructor from {@link MBeanInfo} to {@link MBeanConstructorInfo}
     *
     * @param name
     * @param json
     * @return
     */
    private MBeanConstructorInfo constructorFrom(String name, JSONObject json) {
        String desc = json.get("desc") instanceof String descV ? descV : "";
        Object parametersV = json.get("args");
        Object parameters;

        if (parametersV instanceof JSONArray pJson) {
            parameters = parametersFrom(name, pJson);
        } else {
            throw new IllegalStateException("Parameters from constructor " + name + " are invalid: " + parametersV);
        }

        if (parameters instanceof OpenMBeanParameterInfo[] openParams) {
            return new OpenMBeanConstructorInfoSupport(name, desc, openParams);
        } else {
            return new MBeanConstructorInfo(name, desc, (MBeanParameterInfo[]) parameters);
        }
    }

    /**
     * Convert Jolokia representation of single notification from {@link MBeanInfo} to {@link MBeanNotificationInfo}
     *
     * @param notifications
     * @return
     */
    private MBeanNotificationInfo[] notificationsFrom(JSONObject notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return new MBeanNotificationInfo[0];
        }
        final List<MBeanNotificationInfo> result = new ArrayList<>(notifications.size());

        for (Map.Entry<String, Object> notification : notifications.entrySet()) {
            if (!(notification.getValue() instanceof JSONObject)) {
                throw new IllegalArgumentException("Representation of notification \"" + notification.getKey()
                    + "\" is invalid: " + notification.getValue());
            }
            result.add(notificationFrom(notification.getKey(), (JSONObject) notification.getValue()));
        }

        return result.toArray(MBeanNotificationInfo[]::new);
    }

    /**
     * Convert single Jolokia JSON representation of notification to {@link MBeanNotificationInfo}
     * @param name
     * @param json
     * @return
     */
    private MBeanNotificationInfo notificationFrom(String name, JSONObject json) {
        String desc = json.get("desc") instanceof String descV ? descV : "";

        Object typesV = json.get("types");
        String[] types;
        if (typesV instanceof JSONArray typesArray) {
            types = new String[typesArray.size()];
            int idx = 0;
            for (Object t : typesArray) {
                types[idx++] = t instanceof String ? (String) t : "<unknown>";
            }
        } else {
            throw new IllegalArgumentException("Representation of types from notification \"" + name + "\" is invalid: " + typesV);
        }

        return new MBeanNotificationInfo(types, name, desc);
    }

}
