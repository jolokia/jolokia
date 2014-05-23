package org.jolokia.client;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.testng.annotations.Test;

public class BasicAuthenticatorTest {

    @Test
    public void testPreemptive() throws Exception {
        J4pAuthenticator auth = new BasicAuthenticator().preemptive();
        HttpClientBuilder builder = HttpClientBuilder.create();
        auth.authenticate(builder, "test", "test");
        HttpClient client = builder.build();
        // No way to verify with EasyMock since the builder methods are declared as final ?!
    }

}