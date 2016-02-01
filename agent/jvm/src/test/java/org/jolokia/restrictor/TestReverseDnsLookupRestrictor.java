package org.jolokia.restrictor;/*
 * 
 * Copyright 2015 Roland Huss
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

import javax.management.ObjectName;

import org.jolokia.util.HttpMethod;
import org.jolokia.util.RequestType;

/**
 * @author roland
 * @since 23/01/16
 */
public class TestReverseDnsLookupRestrictor extends AbstractConstantRestrictor {

    public static String[] expectedRemoteHostsToCheck;


    public TestReverseDnsLookupRestrictor() {
        super(true);
    }

    @Override
    public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
        if (pHostOrAddress.length != expectedRemoteHostsToCheck.length) {
            return false;
        }
        for (int i = 0; i < pHostOrAddress.length; i++) {
            if (!expectedRemoteHostsToCheck[i].equals(pHostOrAddress[i])) {
                return false;
            }
        }
        return true;
    }
}
