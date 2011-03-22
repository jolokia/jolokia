package org.jolokia.osgi.restrictor;

import javax.management.ObjectName;

import org.jolokia.restrictor.AllowAllRestrictor;

/**
 * Sample restrictor, which grants read/write/exec access only
 * on a certain JMX-domain.
 *
 * @author roland
 * @since 22.03.11
 */
public class SampleRestrictor extends AllowAllRestrictor {

    private String allowedDomain;

    public SampleRestrictor(String pAllowedDomain) {
        allowedDomain = pAllowedDomain;
    }

    @Override
    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return checkObjectName(pName);
    }

    @Override
    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return checkObjectName(pName);
    }

    @Override
    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return checkObjectName(pName);
    }

    private boolean checkObjectName(ObjectName pName) {
        return pName.getDomain().equals(allowedDomain);
    }
}
