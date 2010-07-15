package org.jolokia.client.request;

import java.util.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * A execute request for executing a JMX operation
 *
 * @author roland
 * @since May 18, 2010
 */
public class J4pExecRequest extends AbtractJ4pMBeanRequest {

    // Operation to execute
    private String operation;

    // Operation arguments
    private List<Object> arguments;

    public J4pExecRequest(ObjectName pMBeanName,String pOperation,Object ... pArgs) {
        super(J4pType.EXEC, pMBeanName);
        operation = pOperation;
        arguments = Arrays.asList(pArgs);
    }

    public J4pExecRequest(String pMBeanName, String pOperation,Object ... pArgs)
            throws MalformedObjectNameException {
        this(new ObjectName(pMBeanName),pOperation,pArgs);
    }

    public String getOperation() {
        return operation;
    }

    public List<Object> getArguments() {
        return arguments;
    }

    @Override
    J4pExecResponse createResponse(JSONObject pResponse) {
        return new J4pExecResponse(this,pResponse);
    }

    @Override
    List<String> getRequestParts() {
        List<String> ret = super.getRequestParts();
        ret.add(operation);
        if (arguments.size() > 0) {
            for (int i = 0; i < arguments.size(); i++) {
                Object arg = arguments.get(i);
                if (arg != null && arg.getClass().isArray()) {
                    ret.add(getArrayForArgument((Object[]) arg));
                } else  {
                    ret.add(nullEscape(arg));
                }
            }
        }
        return ret;
    }

    private String getArrayForArgument(Object[] pArg) {
        StringBuilder inner = new StringBuilder();
        for (int i = 0; i< pArg.length; i++) {
            inner.append(pArg[i].toString());
            if (i < pArg.length - 1) {
                inner.append(",");
            }
        }
        return nullEscape(inner.toString());
    }

    private String nullEscape(Object pArg) {
        if (pArg == null) {
            return "[null]";
        } else if (pArg instanceof String && ((String) pArg).length() == 0) {
            return "\"\"";
        } else {
            return pArg.toString();
        }
    }

    @Override
    JSONObject toJson() {
        JSONObject ret = super.toJson();
        ret.put("operation",operation);
        if (arguments.size() > 0) {
            JSONArray args = new JSONArray();
            for (Object arg : arguments) {
                if (arg != null && arg.getClass().isArray()) {
                    JSONArray innerArray = new JSONArray();
                    for (Object inner : (Object []) arg) {
                        innerArray.add(inner != null ? inner.toString() : "[null]");
                    }
                    args.add(innerArray);
                }
                else {
                    args.add(arg != null ? arg.toString() : "[null]");
                }
            }
            ret.put("arguments",args);
        }
        return ret;
    }
}
