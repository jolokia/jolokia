package org.jolokia.handler;

import org.jolokia.JmxRequest;
import org.jolokia.config.Restrictor;
import org.jolokia.converter.StringToObjectConverter;

import javax.management.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * @author roland
 * @since Jun 12, 2009
 */
public class ExecHandler extends JsonRequestHandler {
    private StringToObjectConverter stringToObjectConverter;

    public ExecHandler(Restrictor pRestrictor,StringToObjectConverter pStringToObjectConverter) {
        super(pRestrictor);
        stringToObjectConverter = pStringToObjectConverter;
    }

    @Override
    public JmxRequest.Type getType() {
        return JmxRequest.Type.EXEC;
    }

    @Override
    protected void checkForRestriction(JmxRequest pRequest) {
        if (!getRestrictor().isOperationAllowed(pRequest.getObjectName(),pRequest.getOperation())) {
            throw new SecurityException("Operation " + pRequest.getOperation() +
                    " forbidden for MBean " + pRequest.getObjectNameAsString());
        }
    }

    /**
     * Execute an JMX operation. The operation name is taken from the request, as well as the
     * arguments to use. If the operation is given in the format "op(type1,type2,...)"
     * (e.g "getText(java.lang.String,int)" then the argument types are taken into account
     * as well. This way, overloaded JMX operation can be used. If an overloaded JMX operation
     * is called without specifying the argument types, then an exception is raised.
     *
     * @param server server to try
     * @param request request to process from where the operation and its arguments are extracted.
     * @return the return value of the operation call
     */
    @Override
    public Object doHandleRequest(MBeanServerConnection server, JmxRequest request)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        OperationAndParamType types = extractOperationTypes(server,request);
        Object[] params = new Object[types.paramClasses.length];
        List<String> args = request.getExtraArgs();
        if (args.size() != types.paramClasses.length) {
            throw new IllegalArgumentException("Invalid operation parameters. Operation " +
                    request.getOperation() + " on " + request.getObjectName() + " requires " + types.paramClasses.length +
                    " parameters, not " + args.size() + " as given");
        }
        for (int i = 0;i <  types.paramClasses.length; i++) {
            params[i] = stringToObjectConverter.convertFromString(types.paramClasses[i],args.get(i));
        }

        // Remove args from request, so that the rest can be interpreted as path for the return
        // value
        for (int i = 0; i < types.paramClasses.length; i++) {
            // Remove from front
            args.remove(0);
        }

        return server.invoke(request.getObjectName(),types.operationName,params,types.paramClasses);
    }

    /**
     * Extract the operation and type list from a given request
     *
     * @param pServer server from which obtain the MBean type info
     * @param pRequest the exec request
     * @return combined object containing the operation name and parameter classes
     */
    private OperationAndParamType extractOperationTypes(MBeanServerConnection pServer, JmxRequest pRequest)
            throws ReflectionException, InstanceNotFoundException, IOException {
        List<String> opArgs = splitOperation(pRequest.getOperation());
        String operation = opArgs.get(0);
        List<String> types = null;
        if (opArgs.size() > 1) {
            types = opArgs.subList(1,opArgs.size());
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
        if (!hasMatchingSignature(types, paramInfos)) {
            throw new IllegalArgumentException(
                    "No operation " + pRequest.getOperation() + " on MBean " + pRequest.getObjectNameAsString() + " exists. " +
                            "Known signatures: " + signatureToString(paramInfos));
        }
        return new OperationAndParamType(operation,types);
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
    private List<MBeanParameterInfo[]> extractMBeanParameterInfos(MBeanServerConnection pServer, JmxRequest pRequest,
                                                                  String pOperation)
            throws InstanceNotFoundException, ReflectionException, IOException {
        try {
            MBeanInfo mBeanInfo = pServer.getMBeanInfo(pRequest.getObjectName());
            List<MBeanParameterInfo[]> paramInfos = new ArrayList<MBeanParameterInfo[]>();
            for (MBeanOperationInfo opInfo : mBeanInfo.getOperations()) {
                if (opInfo.getName().equals(pOperation)) {
                    paramInfos.add(opInfo.getSignature());
                }
            }
            if (paramInfos.size() == 0) {
                throw new IllegalArgumentException("No operation " + pOperation +
                        " found on MBean " + pRequest.getObjectNameAsString());
            }
            return paramInfos;
        }  catch (IntrospectionException e) {
            throw new IllegalStateException("Cannot extract MBeanInfo for " + pRequest.getObjectNameAsString());
        }
    }

    /**
     * Check whether a matching signature exists from a list of MBean parameter infos. The match is done against a list of types
     * (in string form) which was extracted from the request
     *
     * @param pTypes types to match agains. These are full qualified class names in string representation
     * @param pParamInfos list of parameter infos
     * @return a string
     */
    private boolean hasMatchingSignature(List<String> pTypes, List<MBeanParameterInfo[]> pParamInfos) {
        OUTER:
        for (MBeanParameterInfo[]  infos : pParamInfos) {
            if (pTypes.size() != infos.length) {
                // Number of arguments dont match
                continue OUTER;
            }
            for (int i=0;i<infos.length;i++) {
                String type = infos[i].getType();
                if (!type.equals(pTypes.get(i))) {
                    // Non-matching signature
                    continue OUTER;
                }
            }
            // If we did it until here, we are finished.
            return true;
        }
        return false;
    }

    // Extract operation and optional type parameters
    private List<String> splitOperation(String pOperation) {
        List<String> ret = new ArrayList<String>();
        Pattern p = Pattern.compile("^(.*)\\((.*)\\)$");
        Matcher m = p.matcher(pOperation);
        if (m.matches()) {
            ret.add(m.group(1));
            String[] args = m.group(2).split("\\s*,\\s*");
            ret.addAll(Arrays.asList(args));
        } else {
            ret.add(pOperation);
        }
        return ret;
    }

    private String getErrorMessageForMissingSignature(JmxRequest pRequest, String pOperation, List<MBeanParameterInfo[]> pParamInfos) {
        StringBuffer msg = new StringBuffer("Operation ");
        msg.append(pOperation).
                append(" on MBEan ").
                append(pRequest.getObjectNameAsString()).
                append(" is overloaded. Signatures found: ");
        msg.append(signatureToString(pParamInfos));
        msg.append(". Use a signature when specifying the operation.");
        return msg.toString();
    }

    private String signatureToString(List<MBeanParameterInfo[]> pParamInfos) {
        StringBuffer ret = new StringBuffer();
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
        private OperationAndParamType(String pOperationName, List<String> pParamClazzes) {
            operationName = pOperationName;
            paramClasses = new ArrayList<String>(pParamClazzes).toArray(new String[pParamClazzes.size()]);
        }

        private OperationAndParamType(String pOperationName, MBeanParameterInfo[] pParameterInfos) {
            operationName = pOperationName;
            paramClasses = new String[pParameterInfos.length];
            int i=0;
            for (MBeanParameterInfo info : pParameterInfos) {
                paramClasses[i++] = info.getType();
            }
        }

        private String operationName;
        private String paramClasses[];
    }
}
