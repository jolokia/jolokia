package org.jolokia.osgi;

import javax.management.ObjectName;

import org.jolokia.restrictor.*;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;

/**
 * A restrictor which delegate to a RestrictorService if available or denies access
 * if none is available. If multiple services are available, it will grant access
 * only if all restrictors allow
 */
class DelegatingRestrictor extends DenyAllRestrictor implements Restrictor {

    private BundleContext bundleContext;

    DelegatingRestrictor(BundleContext pBundleContext) {
        bundleContext = pBundleContext;
    }

    /**
     * Actual check which delegate to one or more restrictor services if available.
     *
     * @param pCheck a function object for performing the actual check
     * @param args arguments passed through to the  check
     * @return true if all checks return true
     */
    private boolean checkRestrictorService(RestrictorCheck pCheck, Object ... args) {
        try {
            ServiceReference[] serviceRefs = bundleContext.getServiceReferences(Restrictor.class.getName(),null);
            if (serviceRefs != null) {
                boolean ret = true;
                boolean found = false;
                for (ServiceReference serviceRef : serviceRefs) {
                    Restrictor restrictor = (Restrictor) bundleContext.getService(serviceRef);
                if (restrictor != null) {
                    ret = ret && pCheck.check(restrictor,args);
                    found = true;
                }
                }
                return found && ret;
            } else {
                return false;
            }
        } catch (InvalidSyntaxException e) {
            // Will not happen, since we dont use a filter here
            throw new IllegalArgumentException("Impossible exception (we don't use a filter for fetching the services)");
        }
    }

    // ====================================================================

    private static final RestrictorCheck httpMethodCheck = new RestrictorCheck() {
        public boolean check(Restrictor restrictor,Object ... args) {
            return restrictor.isHttpMethodAllowed((HttpMethod) args[0]);
        }
    };

    @Override
    public boolean isHttpMethodAllowed(HttpMethod pMethod) {
        return checkRestrictorService(httpMethodCheck,pMethod);
    }

    // ====================================================================

    private static final RestrictorCheck typeCheck = new RestrictorCheck() {
        public boolean check(Restrictor restrictor,Object ... args) {
            return restrictor.isTypeAllowed((String) args[0]);
        }
    };

    @Override
    public boolean isTypeAllowed(String pType) {
        return checkRestrictorService(typeCheck, pType);
    }

    // ====================================================================

    private static final RestrictorCheck attributeReadCheck = new RestrictorCheck() {
        public boolean check(Restrictor restrictor,Object ... args) {
            return restrictor.isAttributeReadAllowed((ObjectName) args[0], (String) args[1]);
        }
    };

    @Override
    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return checkRestrictorService(attributeReadCheck,pName,pAttribute);
    }

    // ====================================================================

    private static final RestrictorCheck attributeWriteCheck = new RestrictorCheck() {
        public boolean check(Restrictor restrictor,Object ... args) {
            return restrictor.isAttributeWriteAllowed((ObjectName) args[0], (String) args[1]);
        }
    };

    @Override
    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return checkRestrictorService(attributeWriteCheck,pName,pAttribute);
    }

    // ====================================================================

    private static final RestrictorCheck operationCheck = new RestrictorCheck() {
        public boolean check(Restrictor restrictor,Object ... args) {
            return restrictor.isOperationAllowed((ObjectName) args[0], (String) args[1]);
        }
    };

    @Override
    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return checkRestrictorService(operationCheck,pName,pOperation);
    }

    // ====================================================================

    private static final RestrictorCheck remoteCheck = new RestrictorCheck() {
        public boolean check(Restrictor restrictor,Object ... args) {
            String[] argsS = new String[args.length];
            for (int i = 0; i < args.length; i++) {
                argsS[i] = (String) args[i];
            }
            return restrictor.isRemoteAccessAllowed(argsS);
        }
    };

    @Override
    public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
        return checkRestrictorService(remoteCheck,pHostOrAddress);
    }

    // =======================================================================================================

    private interface RestrictorCheck {
        boolean check(Restrictor restrictor,Object ... args);
    }
}
