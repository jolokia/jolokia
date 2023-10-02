package org.jolokia.config.address;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.Assert;
import org.testng.annotations.Test;

/*
 * 
 * Copyright 2020 Georg Tsakumagos
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
 * Testing the {@link DelegatingAddressConfigService}
 * 
 * 
 * @author Georg Tsakumagos
 * @since 21.06.2020
 */
public class DelegatingAddressConfigServiceTest extends AddressConfigServiceTstBase {

    /**
     * Testing the Service if none delegates service is configured
     * @throws UnknownHostException 
     */
    @Test
    public void unconfigured() throws  UnknownHostException {
        DelegatingAddressConfigService service = new DelegatingAddressConfigService();
        Map<String, String> agentConfig = newAgentConfig();

        agentConfig.clear();

        AtomicReference<InetAddress> result = service.optain(agentConfig);
        Assert.assertNotNull(result, ASSERT_NOTNULL_REF);
        Assert.assertEquals(result.get(), InetAddress.getByName(null));
    }

    /**
     * Testing the Service if none delegates service is configured
     */
    @Test
    public void direct_wildcard() {
        DelegatingAddressConfigService service = new DelegatingAddressConfigService();
        Map<String, String> agentConfig = newAgentConfig();

        agentConfig.put(DirectAddressConfigService.CONFIG_KEY, "*");

        AtomicReference<InetAddress> result = service.optain(agentConfig);

        Assert.assertNotNull(result, ASSERT_NOTNULL_REF);
        Assert.assertNull(result.get(), ASSERT_NULL_REF);
    }

    /**
     * Testing the Service prefers an configured
     * {@linkplain DirectAddressConfigService} instead of an greedy
     * {@link NICMatchingConfigService}
     */
    @Test
    public void prefer_Host_NINMatch() {
        DelegatingAddressConfigService service = new DelegatingAddressConfigService();
        Map<String, String> agentConfig = newAgentConfig();

        agentConfig.put(DirectAddressConfigService.CONFIG_KEY, "*");
        agentConfig.put(NICMatchingConfigService.CONFIG_KEY, ".*");

        AtomicReference<InetAddress> result = service.optain(agentConfig);

        Assert.assertNotNull(result, ASSERT_NOTNULL_REF);
        Assert.assertNull(result.get(), ASSERT_NULL_REF);
    }
    
    /**
     * Testing the Service prefers an configured
     * {@linkplain DirectAddressConfigService} instead of an greedy
     * {@link NICMatchingConfigService}
     */
    @Test
    public void prefer_Host_IPMatch() {
        DelegatingAddressConfigService service = new DelegatingAddressConfigService();
        Map<String, String> agentConfig = newAgentConfig();

        agentConfig.put(DirectAddressConfigService.CONFIG_KEY, "*");
        agentConfig.put(IPMatchingConfigService.CONFIG_KEY, ".*");

        AtomicReference<InetAddress> result = service.optain(agentConfig);

        Assert.assertNotNull(result, ASSERT_NOTNULL_REF);
        Assert.assertNull(result.get(), ASSERT_NULL_REF);
    }

}
