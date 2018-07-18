package org.jolokia.restrictor;

import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.util.HttpMethod;
import org.jolokia.util.RequestType;

import javax.management.ObjectName;

/**
 * Created by nevenr on 11/1/15.
 */
public class TestRestrictorWithConfig implements  Restrictor {

    boolean res;

    public TestRestrictorWithConfig(Configuration configuration) {
        res = Boolean.valueOf(configuration.get(ConfigKey.POLICY_LOCATION));
    }

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
    public boolean isOriginAllowed(String pOrigin, boolean pOnlyWhenStrictCheckingIsEnabled) {
        return res;
    }
}
