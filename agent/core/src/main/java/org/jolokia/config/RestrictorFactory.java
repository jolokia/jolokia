package org.jolokia.config;

import java.io.InputStream;

import org.jolokia.LogHandler;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


/**
 * Factory for obtaining the proper {@link org.jolokia.config.Restrictor}
 *
 * @author roland
 * @since Jul 28, 2009
 */
public final class RestrictorFactory {

    private RestrictorFactory() { }

    /**
     * Get the installed restrictor or the {@link org.jolokia.config.AllowAllRestrictor}
     * is no restrictions are in effect.
     *
     * @param pLogHandler log handler for printing out whether access restrictions are used or not
     * @return the restrictor
     */
    public static Restrictor buildRestrictor(LogHandler pLogHandler) {

        InputStream is =
                Thread.currentThread().getContextClassLoader().getResourceAsStream("/jolokia-access.xml");
        if (is != null) {
            pLogHandler.info("jolokia: Using security policy from 'jolokia-access.xml'");
            return new PolicyBasedRestrictor(is);
        } else {
            pLogHandler.info("jolokia: No security policy installed. Access to any MBean attribute and operation is permitted.");
            return new AllowAllRestrictor();
        }
    }
}
