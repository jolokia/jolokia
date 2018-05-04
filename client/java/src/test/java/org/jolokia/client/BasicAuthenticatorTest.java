package org.jolokia.client;

import org.apache.http.impl.client.HttpClientBuilder;
import org.testng.annotations.Test;

public class BasicAuthenticatorTest {

    @Test
    public void testPreemptive() throws Exception {
        J4pAuthenticator auth = new BasicAuthenticator().preemptive();
        HttpClientBuilder builder = HttpClientBuilder.create();
        auth.authenticate(builder, "test", "test");
        builder.build();
    }

}