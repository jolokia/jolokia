package org.jolokia.restrictor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

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
public final class RestrictorFactory {

    private RestrictorFactory() { }

    /**
     * Lookup a restrictor based on an URL
     *
     * @param pLocation classpath or URL representing the location of the policy restrictor
     *
     * @return the restrictor created or <code>null</code> if none could be found.
     * @throws IOException if reading of the policy stream failed
     */
    public static PolicyRestrictor lookupPolicyRestrictor(String pLocation) throws IOException {
        InputStream is = null;
        if (pLocation.startsWith("classpath:")) {
            String path = pLocation.substring("classpath:".length());
            is =  Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            if (is == null) {
                is = RestrictorFactory.class.getResourceAsStream(path);
            }
        } else {
            URL url = new URL(pLocation);
            is = url.openStream();
        }
        return is != null ? new PolicyRestrictor(is) : null;
    }
}
