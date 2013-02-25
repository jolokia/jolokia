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

import java.util.*;

import javax.management.*;
import javax.management.openmbean.*;

import org.jolokia.converter.json.JsonConvertOptions;

/**
 * A {@link DynamicMBean} used to wrap an MBean registered at the Jolokia MBeanServer and translated
 * non-native datatype into JSON strings.
 *
 * @author roland
 * @since 24.01.13
 */
public class JsonDynamicMBeanImpl implements DynamicMBean, MBeanRegistration {

    // String type used for announcing registration infos
    public static final String STRING_TYPE = String.class.getName();

    // Set containing all types which are directly supported and are not converted
    private static final Set<String> DIRECT_TYPES = new HashSet<String>();

    // MBeanInfo for the MBean registered at the delegate server, where complex types are replaced with strings
    private MBeanInfo wrappedMBeanInfo;

    // The hosting Jolokia MBean Server for accessing the serializers
    private JolokiaMBeanServer jolokiaMBeanServer;

    // Name of this MBean
    private ObjectName objectName;

    // Maps holding all attribute and operations infos used during the mapping phase
    private Map<String, MBeanAttributeInfo>     attributeInfoMap;
    private Map<String, List<OperationMapInfo>> operationInfoMap;

    // Options used for converting return values
    private final JsonConvertOptions jsonConvertOptions;

    /**
     * Construct a DynamicMBean wrapping an original MBean object. For attributes
     * and operations all non-trivial data types are translated into Strings
     * for a JSON representation
     *
     * @param pJolokiaMBeanServer the hosting Jolokia MBean Server
     * @param pObjectName         object name of this MBean
     * @param pInfo               the original MBeanInfo
     * @param pConvertOptions     options used for converting return values to JSON
     */
    public JsonDynamicMBeanImpl(JolokiaMBeanServer pJolokiaMBeanServer, ObjectName pObjectName, MBeanInfo pInfo, JsonConvertOptions pConvertOptions) {
        jolokiaMBeanServer = pJolokiaMBeanServer;
        objectName = pObjectName;
        jsonConvertOptions = pConvertOptions != null ? pConvertOptions : JsonConvertOptions.DEFAULT;
        attributeInfoMap = new HashMap<String, MBeanAttributeInfo>();
        operationInfoMap = new HashMap<String, List<OperationMapInfo>>();
        wrappedMBeanInfo = getWrappedInfo(pInfo);
    }

    /** {@inheritDoc} */
    public Object getAttribute(String pAttribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        try {
            if (!attributeInfoMap.containsKey(pAttribute)) {
                return jolokiaMBeanServer.getAttribute(objectName, pAttribute);
            } else {
                return toJson(jolokiaMBeanServer.getAttribute(objectName, pAttribute));
            }
        } catch (InstanceNotFoundException e) {
            AttributeNotFoundException exp = new AttributeNotFoundException("MBean " + objectName + " not found for attribute " + pAttribute);
            exp.initCause(e);
            throw exp;
        }
    }

    /** {@inheritDoc} */
    public void setAttribute(Attribute pAttribute)
            throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
        try {
            if (!attributeInfoMap.containsKey(pAttribute.getName())) {
                jolokiaMBeanServer.setAttribute(objectName, pAttribute);
            } else {
                String name = pAttribute.getName();
                MBeanAttributeInfo info = attributeInfoMap.get(name);
                Object value;
                if (info instanceof OpenMBeanAttributeInfo) {
                    value = fromJson(((OpenMBeanAttributeInfo) info).getOpenType(), (String) pAttribute.getValue());
                } else {
                    value = fromJson(info.getType(), (String) pAttribute.getValue());
                }
                Attribute attr = new Attribute(name, value);
                jolokiaMBeanServer.setAttribute(objectName, attr);
            }
        } catch (InstanceNotFoundException e) {
            AttributeNotFoundException exp = new AttributeNotFoundException("MBean " + objectName + " not found for attribute " + pAttribute);
            exp.initCause(e);
            throw exp;
        }
    }

    /** {@inheritDoc} */
    public Object invoke(String pOperation, Object[] pParams, String[] pSignature)
            throws MBeanException, ReflectionException {
        OperationMapInfo opMapInfo = getOperationMapInfo(pOperation, pSignature);
        try {
            if (opMapInfo == null) {
                return jolokiaMBeanServer.invoke(objectName, pOperation, pParams, pSignature);
            } else {
                return mapAndInvoke(pOperation, pParams, pSignature, opMapInfo);
            }
        } catch (InstanceNotFoundException e) {
            // Should not happen, since the Jolokia MBeanServer and the delegate MBeanServer this bean is registered
            // at are in sync.
            throw new IllegalStateException("Internal: Could find MBean " + objectName +
                                            " on Jolokia MBeanServer. Should be in sync",e);
        }
    }

    /** {@inheritDoc} */
    public AttributeList getAttributes(String[] attributes /* cannot be null */) {
        final AttributeList ret = new AttributeList(attributes.length);
        for (String attrName : attributes) {
            try {
                final Object attrValue = getAttribute(attrName);
                ret.add(new Attribute(attrName, attrValue));
            } catch (Exception e) {
                // Ignore this attribute. As specified in the JMX Spec
            }
        }
        return ret;
    }

    /** {@inheritDoc} */
    public AttributeList setAttributes(AttributeList attributes) {
        final AttributeList ret = new AttributeList(attributes.size());
        for (Object o : attributes) {
            Attribute attr = (Attribute) o;
            try {
                setAttribute(attr);
                ret.add(new Attribute(attr.getName(), getAttribute(attr.getName())));
            } catch (Exception e) {
                // Attribute is not included in returned list. The spec says so
            }
        }
        return ret;
    }

    /** {@inheritDoc} */
    public MBeanInfo getMBeanInfo() {
        return wrappedMBeanInfo;
    }

    // =================================================================================================

    // Delegate serialization/deserialization the Jolokia MBeanServer
    private Object toJson(Object pValue) {
        return jolokiaMBeanServer.toJson(pValue, jsonConvertOptions);
    }

    private Object fromJson(String pType, String pValue) {
        return jolokiaMBeanServer.fromJson(pType, pValue);
    }

    private Object fromJson(OpenType<?> pType, String pValue) {
        return jolokiaMBeanServer.fromJson(pType, pValue);
    }

    // Map the parameters and the return value if required
    private Object mapAndInvoke(String pOperation, Object[] pParams, String[] pSignature, OperationMapInfo pOpMapInfo)
            throws InstanceNotFoundException, MBeanException, ReflectionException {
        // Map parameters
        Object realParams[] = new Object[pSignature.length];
        String realSignature[] = new String[pSignature.length];
        for (int i = 0; i < pSignature.length; i++) {
            if (pOpMapInfo.isParamMapped(i)) {
                String origType = pOpMapInfo.getOriginalType(i);
                OpenType openType = pOpMapInfo.getOpenMBeanType(i);
                if (openType != null) {
                    realParams[i] = fromJson(openType, (String) pParams[i]);
                } else {
                    realParams[i] = fromJson(origType, (String) pParams[i]);
                }
                realSignature[i] = origType;
            } else {
                realParams[i] = pParams[i];
                realSignature[i] = pSignature[i];
            }
        }
        Object ret = jolokiaMBeanServer.invoke(objectName, pOperation, realParams, realSignature);
        return pOpMapInfo.isRetTypeMapped() ? toJson(ret) : ret;
    }

    // Lookup, whether a mapping is required for this call
    private OperationMapInfo getOperationMapInfo(String pOperation, String[] pSignature) {
        List<OperationMapInfo> opMapInfoList = operationInfoMap.get(pOperation);
        OperationMapInfo opMapInfo = null;
        if (opMapInfoList != null) {
            for (OperationMapInfo i : opMapInfoList) {
                if (i.matchSignature(pSignature)) {
                    opMapInfo = i;
                    break;
                }
            }
        }
        return opMapInfo;
    }

    // Check for all type which should *not* be mapped
    private boolean isDirectlySupported(String pType) {
        return DIRECT_TYPES.contains(pType);
    }

    // Wrap the given MBeanInfo, modified for translated signatures
    private MBeanInfo getWrappedInfo(MBeanInfo pMBeanInfo) {
        MBeanAttributeInfo[] attrInfo = getWrappedAttributeInfo(pMBeanInfo);
        MBeanOperationInfo[] opInfo = getWrappedOperationInfo(pMBeanInfo);

        return new MBeanInfo(pMBeanInfo.getClassName(),
                             pMBeanInfo.getDescription(),
                             attrInfo,
                             null, /* We dont allow construction of this MBean, hence null-constructors */
                             opInfo,
                             pMBeanInfo.getNotifications()
        );
    }

    private MBeanAttributeInfo[] getWrappedAttributeInfo(MBeanInfo pMBeanInfo) {
        MBeanAttributeInfo origAttrInfo[] = pMBeanInfo.getAttributes();
        MBeanAttributeInfo attrInfo[] = new MBeanAttributeInfo[origAttrInfo.length];
        for (int i = 0; i < origAttrInfo.length; i++) {
            MBeanAttributeInfo aInfo = origAttrInfo[i];
            String clazz = aInfo.getType();
            String attrType;
            if (isDirectlySupported(clazz)) {
                attrType = clazz;
            } else {
                attrType = STRING_TYPE;
                // Remember type for later conversion when setting an attribute
                attributeInfoMap.put(aInfo.getName(), aInfo);
            }

            attrInfo[i] =
                    new MBeanAttributeInfo(
                            aInfo.getName(),
                            attrType,
                            aInfo.getDescription(),
                            aInfo.isReadable(),
                            aInfo.isWritable(),
                            aInfo.isIs(),
                            aInfo.getDescriptor());
        }
        return attrInfo;
    }

    private MBeanOperationInfo[] getWrappedOperationInfo(MBeanInfo pMBeanInfo) {
        MBeanOperationInfo origOpInfo[] = pMBeanInfo.getOperations();
        MBeanOperationInfo opInfo[] = new MBeanOperationInfo[origOpInfo.length];

        for (int i = 0; i < origOpInfo.length; i++) {
            MBeanOperationInfo oInfo = origOpInfo[i];

            String retType;
            OperationMapInfo opMapInfo;
            if (isDirectlySupported(oInfo.getReturnType())) {
                retType = oInfo.getReturnType();
                opMapInfo = new OperationMapInfo(oInfo, false);
            } else {
                retType = STRING_TYPE;
                opMapInfo = new OperationMapInfo(oInfo, true);
            }
            MBeanParameterInfo[] paramInfo = getWrappedParameterInfo(oInfo, opMapInfo);

            // Remember that we mapped this operation info
            if (opMapInfo.containsMapping()) {
                String name = oInfo.getName();
                List<OperationMapInfo> infos = operationInfoMap.get(name);
                if (infos == null) {
                    infos = new ArrayList<OperationMapInfo>();
                    operationInfoMap.put(name, infos);
                }
                infos.add(opMapInfo);
            }

            opInfo[i] =
                    new MBeanOperationInfo(
                            oInfo.getName(),
                            oInfo.getDescription(),
                            paramInfo,
                            retType,
                            oInfo.getImpact(),
                            oInfo.getDescriptor()
                    );
        }
        return opInfo;
    }

    private MBeanParameterInfo[] getWrappedParameterInfo(MBeanOperationInfo pOInfo, OperationMapInfo pMapInfo) {
        MBeanParameterInfo origParamInfo[] = pOInfo.getSignature();
        MBeanParameterInfo paramInfo[] = new MBeanParameterInfo[origParamInfo.length];

        for (int j = 0; j < origParamInfo.length; j++) {
            MBeanParameterInfo pInfo = origParamInfo[j];
            String pType;
            if (isDirectlySupported(pInfo.getType())) {
                pType = pInfo.getType();
                pMapInfo.pushParamTypes(pType, null, null);
            } else {
                pType = STRING_TYPE;
                if (pInfo instanceof OpenMBeanParameterInfo) {
                    pMapInfo.pushParamTypes(STRING_TYPE, pInfo.getType(), ((OpenMBeanParameterInfo) pInfo).getOpenType());
                } else {
                    pMapInfo.pushParamTypes(STRING_TYPE, pInfo.getType(), null);
                }
            }
            paramInfo[j] = new MBeanParameterInfo(
                    pInfo.getName(),
                    pType,
                    pInfo.getDescription(),
                    pInfo.getDescriptor()
            );
        }
        return paramInfo;
    }

    // ======================================================
    // Lifecycle method for cleaning up
    /** {@inheritDoc} */
    public ObjectName preRegister(MBeanServer server, ObjectName name) {
        return name;
    }

    /** {@inheritDoc} */
    public void postRegister(Boolean registrationDone) {
    }

    /** {@inheritDoc} */
    public void preDeregister() {
    }

    // Cleanup, release all references
    /** {@inheritDoc} */
    public void postDeregister() {
        jolokiaMBeanServer = null;
        wrappedMBeanInfo = null;
        objectName = null;
        attributeInfoMap = null;
        operationInfoMap = null;
    }

    // ==========================================

    private static final class OperationMapInfo {

        private boolean    retTypeMapped;
        private String[]   signature;
        private String[]   origTypes;
        private OpenType[] openMBeanTypes;
        private int        idx;
        private boolean    paramMapped;

        private OperationMapInfo(MBeanOperationInfo pInfo, boolean pRetTypeMapped) {
            retTypeMapped = pRetTypeMapped;
            origTypes = new String[pInfo.getSignature().length];
            openMBeanTypes = new OpenType[pInfo.getSignature().length];
            signature = new String[pInfo.getSignature().length];
            idx = 0;
            paramMapped = false;
        }

        private void pushParamTypes(String pNewType, String pOrigType, OpenType pOpenType) {
            signature[idx] = pNewType;
            origTypes[idx] = pOrigType;
            openMBeanTypes[idx] = pOpenType;
            idx++;
            if (pOrigType != null || pOpenType != null) {
                paramMapped = true;
            }
        }

        private boolean isParamMapped(int pIdx) {
            return origTypes[pIdx] != null;
        }

        private String getOriginalType(int pIdx) {
            return origTypes[pIdx];
        }

        private OpenType getOpenMBeanType(int pIdx) {
            return openMBeanTypes[pIdx];
        }

        private boolean isRetTypeMapped() {
            return retTypeMapped;
        }

        private boolean containsMapping() {
            return retTypeMapped || paramMapped;
        }

        private boolean matchSignature(String pSignature[]) {
            return Arrays.equals(signature, pSignature);
        }
    }

    // =======================================================

    // Static initialized for filling in the set for all directly used types
    static {
        Collections.addAll(DIRECT_TYPES,
                           Byte.class.getName(), "byte",
                           Integer.class.getName(), "int",
                           Long.class.getName(), "long",
                           Short.class.getName(), "short",
                           Double.class.getName(), "double",
                           Float.class.getName(), "float",
                           Boolean.class.getName(), "boolean",
                           Character.class.getName(), "char",
                           String.class.getName());
    }
}
