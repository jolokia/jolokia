package org.jolokia.restrictor;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.Configuration;
import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.util.HttpMethod;
import org.jolokia.server.core.util.RequestType;

import javax.management.ObjectName;

/**
 * Created by nevenr on 11/1/15.
 */
public class TestRestrictorWithConfig implements Restrictor {

    boolean res;

    public TestRestrictorWithConfig(Configuration configuration) {
        res = Boolean.parseBoolean(configuration.getConfig(ConfigKey.POLICY_LOCATION));
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

    @Override
    public boolean isObjectNameHidden(ObjectName name) {
        return res;
    }
}
