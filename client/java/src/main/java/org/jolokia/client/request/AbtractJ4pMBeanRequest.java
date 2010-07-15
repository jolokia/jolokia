package org.jolokia.client.request;

import java.util.ArrayList;
import java.util.List;

import javax.management.ObjectName;

import org.json.simple.JSONObject;

/**
 * A request dealing with a single MBean.
 *
 * @author roland
 * @since Apr 24, 2010
 */
public abstract class AbtractJ4pMBeanRequest extends J4pRequest {

    // name of MBean to execute a request on
    private ObjectName objectName;

    protected AbtractJ4pMBeanRequest(J4pType pType,ObjectName pMBeanName) {
        super(pType);
        objectName = pMBeanName;
    }

    /**
     * Get the object name for the MBean on which this request
     * operates
     *
     * @return MBean's name
     */
    public ObjectName getObjectName() {
        return objectName;
    }

    @Override
    List<String> getRequestParts() {
        List<String> ret = new ArrayList<String>();
        ret.add(objectName.getCanonicalName());
        return ret;
    }

    @Override
    JSONObject toJson() {
        JSONObject ret =  super.toJson();
        ret.put("mbean",objectName.getCanonicalName());
        return ret;
    }
}
