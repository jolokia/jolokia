package org.jolokia.server.core.restrictor;

import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.util.HttpMethod;
import org.jolokia.server.core.util.RequestType;

import javax.management.ObjectName;

/**
 * Created by nevenr on 11/1/15.
 */
public class TestRestrictor implements Restrictor {

    public boolean isHttpMethodAllowed(HttpMethod pMethod) {
        return false;
    }

    public boolean isTypeAllowed(RequestType pType) {
        return false;
    }

    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return false;
    }

    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return false;
    }

    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return false;
    }

    public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
        return false;
    }

    public boolean isOriginAllowed(String pOrigin, boolean pIsStrictCheck) {
        return false;
    }
}
