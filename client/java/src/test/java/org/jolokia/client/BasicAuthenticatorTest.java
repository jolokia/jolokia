package org.jolokia.client;

//import org.apache.http.impl.client.HttpClientBuilder;
import org.jolokia.client.spi.J4pClientCustomizer;
import org.testng.annotations.Test;

public class BasicAuthenticatorTest {

    @Test
    public void testPreemptive() {
        J4pClientCustomizer auth = new BasicClientCustomizer().preemptive();
//        HttpClientBuilder builder = HttpClientBuilder.create();
//        auth.authenticate(builder, "test", "test");
//        builder.build();
    }

}
