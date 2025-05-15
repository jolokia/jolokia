package org.jolokia.server.core.osgi.security;

import javax.management.ObjectName;

import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.util.HttpMethod;
import org.jolokia.server.core.util.RequestType;
import org.osgi.framework.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    private final BundleContext bundleContext;

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
            List<Restrictor> restrictors = getRegisteredRestrictorsFromBundleContext();
            boolean ret = true;
            boolean found = false;
            for(Restrictor restrictor : restrictors) {
                ret = ret && pCheck.check(restrictor, args);
                found = true;
            }
            return found && ret;
        } catch (InvalidSyntaxException e) {
            // Will not happen, since we dont use a filter here
            throw new IllegalArgumentException("Impossible exception (we don't use a filter for fetching the services)",e);
        }
    }

    /**
     * Invoke a function which delegate to one or more restrictor services if available. If
     * more than one Restrictor is there the
     *
     * @param invoker a function object for performing the actual invocation
     * @param args arguments passed through to the  check
     * @return true if all checks return true
     */
    private Object invokeRestrictedAttributeValueRestrictorService(RestrictorInvoker invoker, Object ... args) {
        try {
            List<Restrictor> restrictors = getRegisteredRestrictorsFromBundleContext();
            Object invokedValue = restrictors.isEmpty()  ? null : invoker.invoke(restrictors.get(0), args);
            /**
             * restricted value from the first implementation will be passed to the other Restrictors
             */
            for (int i = 1; i < restrictors.size(); i++) {
                invokedValue = invoker.invoke(restrictors.get(i), args);
                args[2] = invokedValue;
            }
            return invokedValue;
        } catch (InvalidSyntaxException e) {
            // Will not happen, since we dont use a filter here
            throw new IllegalArgumentException("Impossible exception (we don't use a filter for fetching the services)",e);
        }
    }

    /**
     * return the list of registered restrictors
     * @return list of registered restrictors
     * @throws InvalidSyntaxException throws syntax error
     */
    private List<Restrictor> getRegisteredRestrictorsFromBundleContext() throws InvalidSyntaxException {
        ServiceReference[] serviceReferences = bundleContext.getServiceReferences(Restrictor.class.getName(), null);
        if(serviceReferences == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(bundleContext.getServiceReferences(Restrictor.class.getName(), null))
                .map(serviceRef -> (Restrictor) bundleContext.getService(serviceRef))
                .filter(Objects::nonNull)
            .collect(Collectors.toList());
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
        return checkRestrictorService(REMOTE_CHECK, (Object[]) pHostOrAddress);
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

    private static final RestrictorCheck DISABLED_NAME_CHECK = new RestrictorCheck() {
        /** {@inheritDoc} */
        public boolean check(Restrictor restrictor, Object... args) {
            return restrictor.isObjectNameHidden((ObjectName) args[0]);
        }
    };

    /** {@inheritDoc} */
    public boolean isObjectNameHidden(ObjectName name) {
        return checkRestrictorService(DISABLED_NAME_CHECK, name);
    }

    // =======================================================================================================

    private static final RestrictorInvoker RESTRICTED_ATTRIBUTE_VALUE = new RestrictorInvoker() {
        /** {@inheritDoc} */
        public Object invoke(Restrictor restrictor, Object... args) {
            return restrictor.restrictedAttributeValue((ObjectName) args[0], (String) args[1], args[2]);
        }
    };

    /** {@inheritDoc} */
    public Object restrictedAttributeValue(ObjectName pName, String pAttribute, Object object) {
        return invokeRestrictedAttributeValueRestrictorService(RESTRICTED_ATTRIBUTE_VALUE, pName, pAttribute, object);
    }

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
        boolean check(Restrictor restrictor, Object ... args);
    }

    /**
     * Internal interface for restrictor delegation for function invocation
     */
    private interface RestrictorInvoker {
        /**
         * Run check specifically for the restrictor to delegate to
         * @param restrictor the restrictor on which the check should be run
         * @param args context dependent arguments
         * @return result of the check
         */
        Object invoke(Restrictor restrictor, Object ... args);
    }
}
