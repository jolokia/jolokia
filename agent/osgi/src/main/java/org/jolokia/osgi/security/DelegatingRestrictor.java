package org.jolokia.osgi.security;

import javax.management.ObjectName;

import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.HttpMethod;
import org.jolokia.util.RequestType;
import org.osgi.framework.*;

/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * A restrictor which delegate to a RestrictorService if available or denies access
 * if none is available. If multiple services are available, it will grant access
 * only if all restrictors allow
 */
public class DelegatingRestrictor implements Restrictor {

    private BundleContext bundleContext;

    /**
     * Constructor remembering the bundle context
     *
     * @param pBundleContext bundle context to remember
     */
    public DelegatingRestrictor(BundleContext pBundleContext) {
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
            throw new IllegalArgumentException("Impossible exception (we don't use a filter for fetching the services)",e);
        }
    }

    // ====================================================================

    private static final RestrictorCheck HTTP_METHOD_CHECK = new RestrictorCheck() {
        /** {@inheritDoc} */
        public boolean check(Restrictor restrictor,Object ... args) {
            return restrictor.isHttpMethodAllowed((HttpMethod) args[0]);
        }
    };

    /** {@inheritDoc} */
    public boolean isHttpMethodAllowed(HttpMethod pMethod) {
        return checkRestrictorService(HTTP_METHOD_CHECK,pMethod);
    }

    // ====================================================================

    private static final RestrictorCheck TYPE_CHECK = new RestrictorCheck() {
        /** {@inheritDoc} */
        public boolean check(Restrictor restrictor,Object ... args) {
            return restrictor.isTypeAllowed((RequestType) args[0]);
        }
    };

    /** {@inheritDoc} */
    public boolean isTypeAllowed(RequestType pType) {
        return checkRestrictorService(TYPE_CHECK, pType);
    }

    // ====================================================================

    private static final RestrictorCheck ATTRIBUTE_READ_CHECK = new RestrictorCheck() {
        /** {@inheritDoc} */
        public boolean check(Restrictor restrictor,Object ... args) {
            return restrictor.isAttributeReadAllowed((ObjectName) args[0], (String) args[1]);
        }
    };

    /** {@inheritDoc} */
    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return checkRestrictorService(ATTRIBUTE_READ_CHECK,pName,pAttribute);
    }

    // ====================================================================

    private static final RestrictorCheck ATTRIBUTE_WRITE_CHECK = new RestrictorCheck() {
        /** {@inheritDoc} */
        public boolean check(Restrictor restrictor,Object ... args) {
            return restrictor.isAttributeWriteAllowed((ObjectName) args[0], (String) args[1]);
        }
    };

    /** {@inheritDoc} */
    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return checkRestrictorService(ATTRIBUTE_WRITE_CHECK,pName,pAttribute);
    }

    // ====================================================================

    private static final RestrictorCheck OPERATION_CHECK = new RestrictorCheck() {
        /** {@inheritDoc} */
        public boolean check(Restrictor restrictor,Object ... args) {
            return restrictor.isOperationAllowed((ObjectName) args[0], (String) args[1]);
        }
    };

    /** {@inheritDoc} */
    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return checkRestrictorService(OPERATION_CHECK,pName,pOperation);
    }

    // ====================================================================

    private static final RestrictorCheck REMOTE_CHECK = new RestrictorCheck() {
        /** {@inheritDoc} */
        public boolean check(Restrictor restrictor,Object ... args) {
            String[] argsS = new String[args.length];
            for (int i = 0; i < args.length; i++) {
                argsS[i] = (String) args[i];
            }
            return restrictor.isRemoteAccessAllowed(argsS);
        }
    };

    /** {@inheritDoc} */
    public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
        return checkRestrictorService(REMOTE_CHECK,pHostOrAddress);
    }


    // ====================================================================

    private static final RestrictorCheck CORS_CHECK = new RestrictorCheck() {
        /** {@inheritDoc} */
        public boolean check(Restrictor restrictor, Object... args) {
            return restrictor.isOriginAllowed((String) args[0], (Boolean) args[1]);
        }
    };

    /** {@inheritDoc} */
    public boolean isOriginAllowed(String pOrigin, boolean pOnlyWhenStrictCheckingIsEnabled) {
        return checkRestrictorService(CORS_CHECK, pOrigin, pOnlyWhenStrictCheckingIsEnabled);
    }

    // =======================================================================================================

    /**
     * Internal interface for restrictor delegation
     */
    private interface RestrictorCheck {
        /**
         * Run check specifically for the restrictor to delegate to
         * @param restrictor the restrictor on which the check should be run
         * @param args context dependent arguments
         * @return result of the check
         */
        boolean check(Restrictor restrictor,Object ... args);
    }
}
