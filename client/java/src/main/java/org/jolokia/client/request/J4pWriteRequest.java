package org.jolokia.client.request;

import java.util.Arrays;
import java.util.List;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.json.simple.JSONObject;

/**
 * Request for setting the value of an attribute, optionally
 * with an inner path.
 *
 * @author roland
 * @since Jun 5, 2010
 */
public class J4pWriteRequest extends AbtractJ4pMBeanRequest {

    // Name of attribute to set
    private String attribute;

    // Value of the attribute to set
    private Object value;

    // Inner path (optional)
    private String path;

    /**
     * Constructor for a write request
     *
     * @param pMBeanName MBean name which attribute should be set
     * @param pAttribute name of the attribute to set
     * @param pValue new value
     */
    public J4pWriteRequest(ObjectName pMBeanName,String pAttribute,Object pValue,String ... pPath) {
        super(J4pType.WRITE, pMBeanName);
        attribute = pAttribute;
        value = pValue;
        if (pPath != null && pPath.length > 0) {
            path = pPath[0];
        }
    }

    /**
     * Constructor for a write request
     *
     * @param pMBeanName MBean name which attribute should be set
     * @param pAttribute name of the attribute to set
     * @param pValue new value
     * @param pPath optional path for setting an inner value
     * @throws MalformedObjectNameException if the mbean name is invalid
     */
    public J4pWriteRequest(String pMBeanName,String pAttribute, Object pValue,String ... pPath)
            throws MalformedObjectNameException {
        this(new ObjectName(pMBeanName),pAttribute,pValue,pPath);
    }

    @Override
    J4pWriteResponse createResponse(JSONObject pResponse) {
        return new J4pWriteResponse(this,pResponse);
    }

    @Override
    List<String> getRequestParts() {
        List<String> parts = super.getRequestParts();
        parts.add(attribute);
        parts.add(convertToString(value));
        if (path != null) {
            // Split up path
            parts.addAll(Arrays.asList(path.split("/")));
        }
        return parts;
    }

    @Override
    JSONObject toJson() {
        JSONObject ret = super.toJson();
        ret.put("attribute",attribute);
        ret.put("value",convertToString(value));
        if (path != null) {
            ret.put("path",path);
        }
        return ret;
    }

    // Convert an object to its string representation
    private String convertToString(Object pValue) {
        if (pValue == null) {
            return "[null]";
        }
        if (pValue instanceof String && ((String) pValue).length() == 0) {
            return "\"\"";
        }
        // TODO: Expand for array handling and more sophisticated type handling
        return pValue.toString();
    }


    // ==============================================================================================
    /**
     * The attribute encapsulated within this request
     *
     * @return the attribute's name
     */
    public String getAttribute() {
        return attribute;
    }

    /**
     * The new value to set
     * @return value
     */
    public Object getValue() {
        return value;
    }

    /**
     * Get path to value
     *
     * @return path or null if no path is set
     */
    public String getPath() {
        return path;
    }

    /**
     * Set the path to retrieve
     *
     * @param pPath inner path
     */
    public void setPath(String pPath) {
        path = pPath;
    }
}
