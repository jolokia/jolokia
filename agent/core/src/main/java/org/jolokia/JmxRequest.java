package org.jolokia;

import java.util.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.json.simple.JSONObject;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


/**
 * Representation of a JMX request which is converted from an GET or POST HTTP
 * Request. A <code>JmxRequest</code> can be obtained only from a
 * {@link org.jolokia.JmxRequestFactory}
 *
 * @author roland
 * @since Apr 19, 2009
 */
public class JmxRequest {

    /**
     * Enumeration holding the method which
     * lead to this request
     */
    public enum HttpMethod {
        POST("post"),
        GET("get");

        private String method;

        HttpMethod(String pMethod) {
            method = pMethod;
        }

        public String getMethod() {
            return method;
        }
    }

    /**
     * Enumeration for encapsulating the request mode.
     */
    public enum Type {
        // Supported:
        READ("read"),
        LIST("list"),
        WRITE("write"),
        EXEC("exec"),
        VERSION("version"),
        SEARCH("search"),

        // Unsupported:
        REGNOTIF("regnotif"),
        REMNOTIF("remnotif"),
        CONFIG("config");

        private String name;

        private static Map<String,Type> typesByNameMap = new HashMap<String, Type>();

        static {
            for (Type t : Type.values()) {
                typesByNameMap.put(t.getName(),t);
            }
        }

        Type(String pName) {
            name = pName;
        }

        public String getName() {
            return name;
        }

        /**
         * Get the request type by a string representation. This is case insensitive.
         * @param pName the type associated with the given name
         * @return type type looked up
         * @throws IllegalArgumentException if the argument is either <code>null</code> or
         *         does not map to a type.
         */
        public static Type getTypeByName(String pName) {
            if (pName == null) {
                throw new IllegalArgumentException("No type given");
            }
            Type type = typesByNameMap.get(pName.toLowerCase());
            if (type == null) {
                throw new IllegalArgumentException("No type with name '" + pName + "' exists");
            }
            return type;
        }
    };

    // Attributes
    private String objectNameS;
    private ObjectName objectName;
    private List<String> attributeNames;
    private boolean multiAttributeMode = false;
    private String value;
    private List<String> extraArgs;
    private String operation;
    private Type type;
    private TargetConfig targetConfig = null;

    // Processing configuration for tis request object
    private Map<ConfigKey, String> processingConfig = new HashMap<ConfigKey, String>();

    // A value fault handler for dealing with exception when extracting values
    private ValueFaultHandler valueFaultHandler = NOOP_VALUE_FAULT_HANDLER;

    // HTTP method which lead to this request
    private HttpMethod method;

    /**
     * Create a request with the given type (with no MBean name).
     * Constructor used for GET requests without an object name.
     *
     * @param pType requests type
     */
    JmxRequest(Type pType) {
        type = pType;
        method = HttpMethod.GET;
    }

    /**
     * Create a request with given type for a certain MBean.
     * Other parameters of the request need to be set explicitely via a setter.
     * This constructor has to be used only for GET requests.
     *
     * @param pType requests type
     * @param pObjectNameS MBean name in string representation
    * @throws MalformedObjectNameException if the name could not properly be translated
     *         into a JMX {@link javax.management.ObjectName}
     */
    JmxRequest(Type pType,String pObjectNameS) throws MalformedObjectNameException {
        type = pType;
        initObjectName(pObjectNameS);
        method = HttpMethod.GET;
    }

    /**
     * Create a request out of a parameter map as obtained from a POST request. The request
     * itself is marked as a POST request.
     * @param pMap JSON object as map representing the request
     * @throws MalformedObjectNameException if the name could not properly bw translated
     *         into a JMX {@link javax.management.ObjectName}
     */
    JmxRequest(Map<String,?> pMap) throws MalformedObjectNameException {
        type = Type.getTypeByName((String) pMap.get("type"));
        initObjectName((String) pMap.get("mbean"));

        initAttribute(pMap.get("attribute"));
        initPath((String) pMap.get("path"));
        initArguments((List) pMap.get("arguments"));
        value = (String) pMap.get("value");
        operation = (String) pMap.get("operation");
        initTargetConfig((Map) pMap.get("target"));

        initProcessingConfig((Map<String,?>) pMap.get("config"));

        initValueFaultHandler();
        method = HttpMethod.POST;
    }

    public String getObjectNameAsString() {
        return objectNameS;
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public List<String> getExtraArgs() {
        return extraArgs;
    }

    public String getExtraArgsAsPath() {
        if (extraArgs != null && extraArgs.size() > 0) {
            StringBuffer buf = new StringBuffer();
            Iterator<String> it = extraArgs.iterator();
            while (it.hasNext()) {
                buf.append(escapePathPart(it.next()));
                if (it.hasNext()) {
                    buf.append("/");
                }
            }
            return buf.toString();
        } else {
            return null;
        }
    }

    private static String escapePathPart(String pPathPart) {
        return pPathPart.replaceAll("/","\\\\/");
    }

    private static String unescapePathPart(String pPathPart) {
        return pPathPart.replaceAll("\\\\/","/");
    }

    static List<String> splitPath(String pPath) {
        // Split on '/' but not on '\/':
        String[] elements = pPath.split("(?<!\\\\)/+");
        List<String> ret = new ArrayList<String>();
        for (String element : elements) {
            ret.add(unescapePathPart(element));
        }
        return ret;
    }

    public String getValue() {
        return value;
    }

    public Type getType() {
        return type;
    }

    public String getOperation() {
        return operation;
    }

    /**
     * Get a processing configuration or null if not set
     * @param pConfigKey configuration key to fetch
     * @return string value or <code>null</code> if not set
     */
    public final String getProcessingConfig(ConfigKey pConfigKey) {
        return processingConfig.get(pConfigKey);
    }

    /**
     * Get a processing configuration as integer or null
     * if not set
     *
     * @param pConfigKey configuration to lookup
     * @return integer value of configuration or null if not set.
     */
    public Integer getProcessingConfigAsInt(ConfigKey pConfigKey) {
        String intValueS = processingConfig.get(pConfigKey);
        if (intValueS != null) {
            return Integer.parseInt(intValueS);
        } else {
            return null;
        }
    }

    final void setProcessingConfig(String pKey, Object pValue) {
        ConfigKey cKey = ConfigKey.getRequestConfigKey(pKey);
        if (cKey != null) {
            processingConfig.put(cKey,pValue != null ? pValue.toString() : null);
        }
    }

    public String getAttributeName() {
       if (attributeNames == null) {
           return null;
       }
        if (isMultiAttributeMode()) {
            throw new IllegalStateException("Request contains more than one attribute (attrs = " +
                    "" + attributeNames + "). Use getAttributeNames() instead.");
        }
        return attributeNames.get(0);
    }

    public List<String> getAttributeNames() {
        return attributeNames;
    }

    /**
     * Set a single attribute name
     *
     * @param pName name of the attribute to set
     */
    void setAttributeName(String pName) {
        attributeNames = Arrays.asList(pName);
        multiAttributeMode = false;
    }

    /**
     * Set null, one or more attribtue names
     *
     * @param pAttributeNames attribute names to set. If this list is <code>null</code> a 'null' attribute name
     *        is set (which denotes *all* attributes of a certain MBean). If this list contains a single element,
     *        this is the same as calling {@link #setAttributeName(String)} with this single element.
     */
    void setAttributeNames(List<String> pAttributeNames) {
        if (pAttributeNames == null || (pAttributeNames.size() == 1 && pAttributeNames.get(0) == null)) {
            setAttributeName(null);
        } else {
            attributeNames = new ArrayList<String>(pAttributeNames);
            multiAttributeMode = true;
        }
    }

    /**
     * Whether this is a multi-attribute request, i.e. whether it contains one ore more attributes to fetch
     * @return true if this is a multi attribute request, false otherwise.
     */
    public boolean isMultiAttributeMode() {
        return multiAttributeMode;
    }

    /**
     * Whether this request has no attribute names associated  (which normally means, that all attributes should be fetched).
     * @return true if no attribute name is stored.
     */
    public boolean hasAttribute() {
        return isMultiAttributeMode() || getAttributeName() != null;
    }

    void setValue(String pValue) {
        value = pValue;
    }

    void setOperation(String pOperation) {
        operation = pOperation;
    }

    void setExtraArgs(List<String> pExtraArgs) {
        extraArgs = pExtraArgs;
    }

    public TargetConfig getTargetConfig() {
        return targetConfig;
    }

    public String getTargetConfigUrl() {
        if (targetConfig == null) {
            return null;
        } else {
            return targetConfig.getUrl();
        }
    }

    public HttpMethod getHttpMethod() {
        return method;
    }

    @Override
    public String toString() {
        StringBuffer ret = new StringBuffer("JmxRequest[");
        if (type == Type.READ) {
            appendReadParameters(ret);
        } else if (type == Type.WRITE) {
            ret.append("WRITE mbean=").append(objectNameS).append(", attribute=").append(getAttributeName())
                    .append(", value=").append(value);
        } else if (type == Type.EXEC) {
            ret.append("EXEC mbean=").append(objectNameS).append(", operation=").append(operation);
        } else {
            ret.append(type).append(" mbean=").append(objectNameS);
        }

        if (extraArgs != null && extraArgs.size() > 0) {
            ret.append(", extra=").append(extraArgs);
        }
        if (targetConfig != null) {
            ret.append(", target=").append(targetConfig);
        }
        ret.append("]");
        return ret.toString();
    }

    private void appendReadParameters(StringBuffer pRet) {
        pRet.append("READ mbean=").append(objectNameS);
        if (attributeNames != null && attributeNames.size() > 1) {
            pRet.append(", attribute=[");
            for (int i = 0;i<attributeNames.size();i++) {
                pRet.append(attributeNames.get(i));
                if (i < attributeNames.size() - 1) {
                    pRet.append(",");
                }
            }
            pRet.append("]");
        } else {
            pRet.append(", attribute=").append(getAttributeName());
        }
    }

    /**
     * Return this request in a proper JSON representation
     * @return this object in a JSON representation
     */
    public JSONObject toJSON() {
        JSONObject ret = new JSONObject();
        ret.put("type",type.name);
        if (objectName != null) {
            ret.put("mbean",objectName.getCanonicalName());
        }
        addAttributesAsJson(ret);
        addExtraArgsAsJson(ret);
        if (value != null) {
            ret.put("value", value);
        }
        if (operation != null) {
            ret.put("operation", operation);
        }

        if (targetConfig != null) {
            ret.put("target", targetConfig.toJSON());
        }
        return ret;
    }

    private void addAttributesAsJson(JSONObject pJsonObject) {
        if (attributeNames != null && attributeNames.size() > 0) {
            if (attributeNames.size() > 1) {
                pJsonObject.put("attribute",attributeNames);
            } else {
                pJsonObject.put("attribute",attributeNames.get(0));
            }
        }
    }

    private void addExtraArgsAsJson(JSONObject pJsonObject) {
        if (extraArgs != null && extraArgs.size() > 0) {
            if (type == Type.READ || type == Type.WRITE) {
                pJsonObject.put("path",getExtraArgsAsPath());
            } else if (type == Type.EXEC) {
                pJsonObject.put("arguments",extraArgs);
            }
        }
    }

    // =====================================================================================================
    // Initializations via a map

    private void initObjectName(String pObjectName) throws MalformedObjectNameException {
        if (pObjectName != null) {
            objectNameS = pObjectName;
            objectName = new ObjectName(objectNameS);
        }
    }

    final void initValueFaultHandler() {
        String s;
        s = getProcessingConfig(ConfigKey.IGNORE_ERRORS);
        if (s != null && s.matches("^(true|yes|on|1)$")) {
            valueFaultHandler = IGNORE_VALUE_FAULT_HANDLER;
        }
    }

    private void initProcessingConfig(Map<String, ?> pConfig) {
        if (pConfig != null) {
            for (Map.Entry<String,?> entry : pConfig.entrySet()) {
                setProcessingConfig(entry.getKey(),entry.getValue());
            }
        }
    }

    private void initTargetConfig(Map pTarget) {
        if (pTarget != null) {
            targetConfig = new TargetConfig(pTarget);
        }
    }

    private void initArguments(List pArguments) {
        if (pArguments != null && pArguments.size() > 0) {
            extraArgs = new ArrayList<String>();
            for (Object val : pArguments) {
                if (val instanceof List) {
                    extraArgs.add(listToString((List) val));
                } else {
                    extraArgs.add(val != null ? val.toString() : null);
                }
            }
        }
    }

    private String listToString(List pList) {
        StringBuilder arrayArg = new StringBuilder();
        for (int i = 0; i < pList.size(); i++) {
            arrayArg.append(pList.get(i) != null ? pList.get(i).toString() : "[null]");
            if (i < pList.size() - 1) {
                arrayArg.append(",");
            }
        }
        return arrayArg.toString();
    }

    private void initPath(String pPath) {
        if (pPath != null) {
            extraArgs = splitPath(pPath);
        } else {
            extraArgs = new ArrayList<String>();
        }
    }

    private void initAttribute(Object pAttrval) {
        if (pAttrval != null) {
            if (pAttrval instanceof String) {
                attributeNames = Arrays.asList((String) pAttrval);
                multiAttributeMode = false;
            } else if (pAttrval instanceof Collection) {
                Collection<String> attributes = (Collection<String>) pAttrval;
                if (attributes.size() == 1 && attributes.iterator().next() == null) {
                    attributeNames = Arrays.asList((String) null);
                } else {
                    attributeNames = new ArrayList<String>(attributes);
                    multiAttributeMode = true;
                }
            }
        }
    }

    /**
     * Handle an exception happened during value extraction
     *
     * @param pFault the fault raised
     * @return a replacement value if this should be used instead or the exception is rethrown if
     *         the handler doesn't handle it
     */
    public <T extends Throwable> Object handleValueFault(T pFault) throws T {
        return valueFaultHandler.handleException(pFault);
    }

    /**
     * Get tha value fault handler, which can be passwed around. {@link #handleValueFault(Throwable)}
     * @return the value fault handler
     */
    public ValueFaultHandler getValueFaultHandler() {
        return valueFaultHandler;
    }

    // ===============================================================================
    // Proxy configuration

    public static class TargetConfig {
        private String url;
        private Map<String,Object> env;

        public TargetConfig(Map pMap) {
            url = (String) pMap.get("url");
            if (url == null) {
                throw new IllegalArgumentException("No service url given for JSR-160 target");
            }
            String user = (String) pMap.get("user");
            if (user != null) {
                env = new HashMap<String, Object>();
                env.put("user",user);
                String pwd = (String) pMap.get("password");
                if (pwd != null) {
                    env.put("password",pwd);
                }
            }
        }

        public String getUrl() {
            return url;
        }

        public Map<String, Object> getEnv() {
            return env;
        }

        public JSONObject toJSON() {
            JSONObject ret = new JSONObject();
            ret.put("url", url);
            if (env != null) {
                ret.put("env", env);
            }
            return ret;
        }

        @Override
        public String toString() {
            return "TargetConfig[" +
                    url +
                    ", " + env +
                    "]";
        }
    }

    // =======================================================================================================
    // Inner interface in order to deal with value exception
    public interface ValueFaultHandler {
        /**
         * Handle the given exception and return an object
         * which can be used as a replacement for the real
         * value
         *
         * @param exception exception to ignore
         * @return replacement value or the exception is rethrown if this handler doesnt handle this exception
         * @throws T if the handler doesnt handel the exception
         */
        <T extends Throwable> Object handleException(T exception) throws T;
    }

    private static final ValueFaultHandler NOOP_VALUE_FAULT_HANDLER = new ValueFaultHandler() {
        public <T extends Throwable> Object handleException(T exception) throws T {
            // Dont handle exception on our own, we rethrow it
            throw exception;
        }
    };

    private static final  ValueFaultHandler IGNORE_VALUE_FAULT_HANDLER = new ValueFaultHandler() {
        public <T extends Throwable> Object handleException(T exception) throws T {
            return "ERROR: " + exception.getMessage() + " (" + exception.getClass() + ")";
        }
    };


}
