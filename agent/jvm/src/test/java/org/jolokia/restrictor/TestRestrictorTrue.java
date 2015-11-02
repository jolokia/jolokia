package org.jolokia.restrictor;

import org.jolokia.util.HttpMethod;
import org.jolokia.util.RequestType;

import javax.management.ObjectName;

/**
 * Created by nevenr on 11/1/15.
 */
public class TestRestrictorTrue implements Restrictor {

    private boolean res = true;

    @Override
    public boolean isHttpMethodAllowed(HttpMethod pMethod) {
        return res;
    }

    @Override
    public boolean isTypeAllowed(RequestType pType) {
        return res;
    }

    @Override
    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return res;
    }

    @Override
    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return res;
    }

    @Override
    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return res;
    }

    @Override
    public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
        return res;
    }

    @Override
    public boolean isOriginAllowed(String pOrigin, boolean pIsStrictCheck) {
        return res;
    }
}
