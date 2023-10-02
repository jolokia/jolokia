package org.jolokia.config.address;

import java.net.InetAddress;
import java.net.SocketException;
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
 * Testing {@link DirectAddressConfigService}
 * 
 * @author Georg Tsakumagos
 * @since 21.06.2020
 *
 */
public class DirectAddressConfigServiceTest extends AddressConfigServiceTstBase {
    
    /**
     * Test the behavior if configuring an invalid regular expression.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void badPattern() {
        DirectAddressConfigService service = new DirectAddressConfigService();
        Map<String, String> agentConfig = newAgentConfig();
        
        agentConfig.put(DirectAddressConfigService.CONFIG_KEY, PATTERN_BAD );
        service.optain(agentConfig );
    }

    
    /**
     * Test the behavior if the service configuration contains an empty string.
     * @throws SocketException If something went wrong enumerate the local interfaces.
     * @throws UnknownHostException If resvoling loopback interface fail.
     */
    @Test()
    public void empty_EMPTY() throws SocketException, UnknownHostException {
        DirectAddressConfigService service = new DirectAddressConfigService();
        InetAddress loopback = InetAddress.getByName(null);
        Map<String, String> agentConfig = newAgentConfig();
        
        agentConfig.put(DirectAddressConfigService.CONFIG_KEY, "");
        
        AtomicReference<InetAddress> result = service.optain(agentConfig);
        
        Assert.assertNotNull(result, ASSERT_NOTNULL_REF);
        Assert.assertEquals(result.get(), loopback);
    }
    
    /**
     * Test the behavior if the service configuration contains <code>null</code> value.
     * @throws SocketException If something went wrong enumerate the local interfaces.
     * @throws UnknownHostException If resvoling loopback interface fail.
     */
    @Test()
    public void empty_NULL() throws SocketException, UnknownHostException {
        DirectAddressConfigService service = new DirectAddressConfigService();
        InetAddress loopback = InetAddress.getByName(null);
        Map<String, String> agentConfig = newAgentConfig();
        
        agentConfig.put(DirectAddressConfigService.CONFIG_KEY, null);
        
        AtomicReference<InetAddress> result = service.optain(agentConfig);
        
        Assert.assertNotNull(result, ASSERT_NOTNULL_REF);
        Assert.assertEquals(result.get(), loopback);
    }
    
    /**
     * Test the behavior if configuring an invalid regular expression.
     * @throws SocketException If something went wrong enumerate the local interfaces.
     */
    @Test()
    public void matching_IP() throws SocketException {
        InetAddress refIP = this.getInterfaceIP();
        
        DirectAddressConfigService service = new DirectAddressConfigService();
        Map<String, String> agentConfig = newAgentConfig();
        
        agentConfig.put(DirectAddressConfigService.CONFIG_KEY, refIP.getHostAddress() );
        
        AtomicReference<InetAddress> result = service.optain(agentConfig);
        
        Assert.assertNotNull(result, ASSERT_NOTNULL_REF);
        Assert.assertEquals(result.get(), refIP);
    }
    
    /**
     * Test the behavior if matching all IPs by wildcard.
     * @throws SocketException If something went wrong enumerate the local interfaces.
     */
    @Test()
    public void matching_Wildcard_Asterix() throws SocketException {
        DirectAddressConfigService service = new DirectAddressConfigService();
        Map<String, String> agentConfig = newAgentConfig();
        
        agentConfig.put(DirectAddressConfigService.CONFIG_KEY, "*" );
        
        AtomicReference<InetAddress> result = service.optain(agentConfig);
        
        Assert.assertNotNull(result, ASSERT_NOTNULL_REF);
        Assert.assertNull(result.get(), ASSERT_NULL_REF);
    }
    
    /**
     * Test the behavior if matching all IPs by wildcard.
     * @throws SocketException If something went wrong enumerate the local interfaces.
     */
    @Test()
    public void matching_Wildcard_Zeros() throws SocketException {
        DirectAddressConfigService service = new DirectAddressConfigService();
        Map<String, String> agentConfig = newAgentConfig();
        
        agentConfig.put(DirectAddressConfigService.CONFIG_KEY, "0.0.0.0" );
        
        AtomicReference<InetAddress> result = service.optain(agentConfig);
        
        Assert.assertNotNull(result, ASSERT_NOTNULL_REF);
        Assert.assertNull(result.get(), ASSERT_NULL_REF);
    }
    
    /**
     * Test the behavior if configuring a matcher that do not find a IP.
     * @throws SocketException If something went wrong enumerate the local interfaces.
     * @throws UnknownHostException If resvoling loopback interface fail.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nonMatching() throws SocketException, UnknownHostException {
        DirectAddressConfigService service = new DirectAddressConfigService();
        InetAddress loopback = InetAddress.getByName(null);
        Map<String, String> agentConfig = newAgentConfig();
        
        agentConfig.put(DirectAddressConfigService.CONFIG_KEY, "999.999.999.999" );
        
        AtomicReference<InetAddress> result = service.optain(agentConfig);
        
        Assert.assertNotNull(result, ASSERT_NOTNULL_REF);
        Assert.assertEquals(result.get(), loopback);
    }
    
    /**
     * Test the behavior if the service is not configured.
     * @throws SocketException If something went wrong enumerate the local interfaces.
     * @throws UnknownHostException If resvoling loopback interface fail.
     */
    @Test()
    public void notConfigured() throws SocketException, UnknownHostException {
        DirectAddressConfigService service = new DirectAddressConfigService();
        Map<String, String> agentConfig = newAgentConfig();
        
        agentConfig.remove(DirectAddressConfigService.CONFIG_KEY);
        
        AtomicReference<InetAddress> result = service.optain(agentConfig);
        Assert.assertNull(result, ASSERT_NULL_REF);
    }
}
