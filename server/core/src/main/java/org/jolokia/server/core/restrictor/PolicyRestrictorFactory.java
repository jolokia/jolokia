package org.jolokia.server.core.restrictor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.jolokia.server.core.restrictor.policy.PolicyRestrictor;
import org.jolokia.server.core.service.LogHandler;
import org.jolokia.server.core.service.Restrictor;
import org.jolokia.server.core.config.ConfigKey;

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
 * Factory for obtaining the proper {@link Restrictor}
 *
 * @author roland
 * @since Jul 28, 2009
 */
public final class PolicyRestrictorFactory {

    private PolicyRestrictorFactory() {  }

    /**
     * Create a restrictor restrictor to use. By default, a policy file
     * is looked up (with the URL given by the init parameter {@link ConfigKey#POLICY_LOCATION}
     * or "/jolokia-access.xml" by default) and if not found an {@link AllowAllRestrictor} is
     * used by default.
     *
     * @param location location from where to lookup the policy restrictor
     * @param log handle for doing the logs
     * @return the restrictor to use.
     */
    public static Restrictor createRestrictor(String location,LogHandler log) {
        try {
            PolicyRestrictor newRestrictor = lookupPolicyRestrictor(location);
            if (newRestrictor != null) {
                log.info("Using access restrictor " + location);
                return newRestrictor;
            } else {
                log.info("No access restrictor found at " + location + ", access to all MBeans is allowed");
                return new AllowAllRestrictor();
            }
        } catch (IOException e) {
            log.error("Error while accessing access restrictor at " + location +
                      ". Denying all access to MBeans for security reasons. Exception: " + e, e);
            return new DenyAllRestrictor();
        }
    }


    /**
     * Lookup a restrictor based on an URL
     *
     * @param pLocation classpath or URL representing the location of the policy restrictor
     *
     * @return the restrictor created or <code>null</code> if none could be found.
     * @throws IOException if reading of the policy stream failed
     */
    private static PolicyRestrictor lookupPolicyRestrictor(String pLocation) throws IOException {
        InputStream is;
        if (pLocation.startsWith("classpath:")) {
            String path = pLocation.substring("classpath:".length());
            is =  Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            if (is == null) {
                is = PolicyRestrictorFactory.class.getResourceAsStream(path);
            }
        } else {
            URL url = new URL(pLocation);
            is = url.openStream();
        }
        return is != null ? new PolicyRestrictor(is) : null;
    }
}
