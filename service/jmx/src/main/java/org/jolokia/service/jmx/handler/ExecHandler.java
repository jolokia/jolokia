package org.jolokia.service.jmx.handler;

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

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.*;
import javax.management.openmbean.OpenMBeanParameterInfo;
import javax.management.openmbean.OpenType;

import org.jolokia.json.JSONObject;
import org.jolokia.server.core.request.JolokiaExecRequest;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.core.service.serializer.ValueFaultHandler;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;


/**
 * Handler for dealing with execute requests.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class ExecHandler extends AbstractCommandHandler<JolokiaExecRequest> {


    /** {@inheritDoc} */
    public RequestType getType() {
        return RequestType.EXEC;
    }

    /** {@inheritDoc} */
    @Override
    protected void checkForRestriction(JolokiaExecRequest pRequest) {
        if (!context.isOperationAllowed(pRequest.getObjectName(),pRequest.getOperation())) {
            throw new SecurityException("Operation " + pRequest.getOperation() +
                    " forbidden for MBean " + pRequest.getObjectNameAsString());
        }
    }

    /**
     * EXEC may be performed on multiple objects if they match the {@link ObjectName} pattern
     * @param pRequest
     * @return
     */
    @Override
    public boolean handleAllServersAtOnce(JolokiaExecRequest pRequest) {
        return pRequest.getObjectName().isPattern();
    }

    /**
     * Execute an JMX operation. The operation name is taken from the request, as well as the
     * arguments to use. If the operation is given in the format "op(type1,type2,...)"
     * (e.g "getText(java.lang.String,int)" then the argument types are taken into account
     * as well. This way, overloaded JMX operation can be used. If an overloaded JMX operation
     * is called without specifying the argument types, then an exception is raised.
     *
     *
     * @param server server to try
     * @param request request to process from where the operation and its arguments are extracted.
     * @return the return value of the operation call
     */
    @Override
    public Object doHandleSingleServerRequest(MBeanServerConnection server, JolokiaExecRequest request)
            throws InstanceNotFoundException, ReflectionException, MBeanException, IOException {
        OperationAndParamType types = extractOperationTypes(server,request);
        int nrParams = types.paramClasses.length;
        Object[] params = new Object[nrParams];
        List<?> args = request.getArguments();
        // all path elements were consumed as arguments, but if there's more, we'll treat them as path for return value
        // they should be Strings
        if (args != null && nrParams < args.size()) {
            List<Object> trail = new ArrayList<>(args.subList(nrParams, args.size()));
            List<String> path = new LinkedList<>();
            for (Object el : trail) {
                if (el instanceof String) {
                    path.add((String) el);
                }
            }
            request.splitArgumentsAndPath(nrParams, path);

            args = request.getArguments();
        } else if (args == null) {
            args = Collections.emptyList();
        }
        verifyArguments(request, types, nrParams, args);
        for (int i = 0; i < nrParams; i++) {
            if (types.paramOpenTypes[i] != null) {
                params[i] = context.getMandatoryService(Serializer.class).deserializeOpenType(types.paramOpenTypes[i], args.get(i));
            } else {
                params[i] = context.getMandatoryService(Serializer.class).deserialize(types.paramClasses[i], args.get(i));
            }
        }

        return server.invoke(request.getObjectName(),types.operationName,params,types.paramClasses);
    }

    @Override
    public Object doHandleAllServerRequest(MBeanServerAccess pServerManager, JolokiaExecRequest pRequest, Object pPreviousResult)
        throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        ObjectName oName = pRequest.getObjectName();
        ValueFaultHandler faultHandler = pRequest.getValueFaultHandler();
        if (oName.isPattern()) {
            // search first
            Set<ObjectName> names = pServerManager.queryNames(oName);
            if (names.isEmpty()) {
                throw new InstanceNotFoundException("No MBean with pattern " + oName + " found for invoking JMX operations");
            }
            JSONObject result = new JSONObject();
            for (ObjectName name : names) {
                Object singleResult = pServerManager.call(name, (connection, objectName, extra)
                    -> ExecHandler.this.doHandleSingleServerRequest(connection, pRequest.withChangedObjectName(name)));
                result.put(pRequest.getOrderedObjectName(name), singleResult);
            }
            return result;
        } else {
            return pServerManager.call(oName, (connection, objectName, extra)
                -> ExecHandler.this.doHandleSingleServerRequest(connection, pRequest));
        }
    }

    // check whether the given arguments are compatible with the signature and if not so, raise an excepton
    private void verifyArguments(JolokiaExecRequest request, OperationAndParamType pTypes, int pNrParams, List<?> pArgs) {
        if ( (pNrParams > 0 && pArgs == null) || (pArgs != null && pArgs.size() != pNrParams)) {
            throw new IllegalArgumentException("Invalid number of operation arguments. Operation " +
                    request.getOperation() + " on " + request.getObjectName() + " requires " + pTypes.paramClasses.length +
                    " parameters, not " + (pArgs == null ? 0 : pArgs.size()) + " as given");
        }
    }

    /**
     * Extract the operation and type list from a given request
     *
     * @param pServer server from which obtain the MBean type info
     * @param pRequest the exec request
     * @return combined object containing the operation name and parameter classes
     */
    private OperationAndParamType extractOperationTypes(MBeanServerConnection pServer, JolokiaExecRequest pRequest)
            throws ReflectionException, InstanceNotFoundException, IOException {
        if (pRequest.getOperation() == null) {
            throw new IllegalArgumentException("No operation given for exec Request on MBean " + pRequest.getObjectName());
        }
        List<String> opArgs = splitOperation(pRequest.getOperation());
        String operation = opArgs.get(0);
        List<String> types;
        if (opArgs.size() > 1) {
            if (opArgs.size() == 2 && opArgs.get(1) == null) {
                // Empty signature requested
                types = Collections.emptyList();
            } else {
                types = opArgs.subList(1,opArgs.size());
            }
        } else {
            List<MBeanParameterInfo[]> paramInfos = extractMBeanParameterInfos(pServer, pRequest, operation);
            if (paramInfos.size() == 1) {
                return new OperationAndParamType(operation,paramInfos.get(0));
            } else {
                // type requested from the operation
                throw new IllegalArgumentException(
                        getErrorMessageForMissingSignature(pRequest, operation, paramInfos));
            }
        }

        List<MBeanParameterInfo[]> paramInfos = extractMBeanParameterInfos(pServer, pRequest, operation);
        MBeanParameterInfo[] matchingSignature = getMatchingSignature(types, paramInfos);
        if (matchingSignature == null) {
            throw new IllegalArgumentException(
                    "No operation " + pRequest.getOperation() + " on MBean " + pRequest.getObjectNameAsString() + " exists. " +
                            "Known signatures: " + signatureToString(paramInfos));
        }
        return new OperationAndParamType(operation, matchingSignature);
    }

    /**
     * Extract a list of operation signatures which match a certain operation name. The returned list
     * can contain multiple signature in case of overloaded JMX operations.
     *
     * @param pServer server from where to fetch the MBean info for a given request's object name
     * @param pRequest the JMX request
     * @param pOperation the operation whose signature should be extracted
     * @return a list of signature. If the operation is overloaded, this contains mutliple entries,
     *         otherwise only a single entry is contained
     */
    private List<MBeanParameterInfo[]> extractMBeanParameterInfos(MBeanServerConnection pServer, JolokiaExecRequest pRequest,
                                                                  String pOperation)
            throws InstanceNotFoundException, ReflectionException, IOException {
        try {
            MBeanInfo mBeanInfo = pServer.getMBeanInfo(pRequest.getObjectName());
            List<MBeanParameterInfo[]> paramInfos = new ArrayList<>();
            for (MBeanOperationInfo opInfo : mBeanInfo.getOperations()) {
                if (opInfo.getName().equals(pOperation)) {
                    paramInfos.add(opInfo.getSignature());
                }
            }
            if (paramInfos.isEmpty()) {
                throw new IllegalArgumentException("No operation " + pOperation +
                        " found on MBean " + pRequest.getObjectNameAsString());
            }
            return paramInfos;
        }  catch (IntrospectionException e) {
            throw new IllegalStateException("Cannot extract MBeanInfo for " + pRequest.getObjectNameAsString(),e);
        }
    }

    /**
     * Check whether a matching signature exists from a list of MBean parameter infos. The match is done against a list of types
     * (in string form) which was extracted from the request
     *
     * @param pTypes types to match agains. These are full qualified class names in string representation
     * @param pParamInfos list of parameter infos
     * @return the matched signature MBeanParamaterInfo[]
     */
    private MBeanParameterInfo[] getMatchingSignature(List<String> pTypes, List<MBeanParameterInfo[]> pParamInfos) {
        OUTER:
        for (MBeanParameterInfo[]  infos : pParamInfos) {
            if (infos.length == 0 && pTypes.isEmpty()) {
                // No-arg argument
                return infos;
            }
            if (pTypes.size() != infos.length) {
                // Number of arguments dont match
                continue;
            }
            for (int i=0;i<infos.length;i++) {
                String type = infos[i].getType();
                if (!type.equals(pTypes.get(i))) {
                    // Non-matching signature
                    continue OUTER;
                }
            }
            // If we did it until here, we are finished.
            return infos;
        }
        return null;
    }

    // Extract operation and optional type parameters
    private List<String> splitOperation(String pOperation) {
        List<String> ret = new ArrayList<>();
        Pattern p = Pattern.compile("^(.*)\\((.*)\\)$");
        Matcher m = p.matcher(pOperation);
        if (m.matches()) {
            ret.add(m.group(1));
            if (!m.group(2).isEmpty()) {
                // No escaping required since the parts a Java types which does not
                // allow for commas
                String[] args = m.group(2).split("\\s*,\\s*");
                ret.addAll(Arrays.asList(args));
            } else {
                // It's "()" which means a no-arg method
                ret.add(null);
            }
        } else {
            ret.add(pOperation);
        }
        return ret;
    }

    private String getErrorMessageForMissingSignature(JolokiaExecRequest pRequest, String pOperation, List<MBeanParameterInfo[]> pParamInfos) {
        StringBuilder msg = new StringBuilder("Operation ");
        msg.append(pOperation).
                append(" on MBean ").
                append(pRequest.getObjectNameAsString()).
                append(" is overloaded. Signatures found: ");
        msg.append(signatureToString(pParamInfos));
        msg.append(". Use a signature when specifying the operation.");
        return msg.toString();
    }

    private String signatureToString(List<MBeanParameterInfo[]> pParamInfos) {
        StringBuilder ret = new StringBuilder();
        for (MBeanParameterInfo[] ii : pParamInfos) {
            ret.append("(");
            for (MBeanParameterInfo i : ii) {
                ret.append(i.getType()).append(",");
            }
            ret.setLength(ret.length()-1);
            ret.append("),");
        }
        ret.setLength(ret.length()-1);
        return ret.toString();
    }

    // ==================================================================================
    // Used for parsing
    private static final class OperationAndParamType {
        private OperationAndParamType(String pOperationName, MBeanParameterInfo[] pParameterInfos) {
            operationName = pOperationName;
            paramClasses = new String[pParameterInfos.length];
            paramOpenTypes = new OpenType<?>[pParameterInfos.length];
            int i=0;
            for (MBeanParameterInfo info : pParameterInfos) {
            	if (info instanceof OpenMBeanParameterInfo) {
            		OpenMBeanParameterInfo openTypeInfo = (OpenMBeanParameterInfo) info;
            		paramOpenTypes[i] = openTypeInfo.getOpenType();
            	}
           		paramClasses[i++] = info.getType();
            }
        }

        private final String operationName;
        private final String[] paramClasses;
        private final OpenType<?>[] paramOpenTypes;
    }
}
