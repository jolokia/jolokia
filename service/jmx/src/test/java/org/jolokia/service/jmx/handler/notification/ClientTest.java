package org.jolokia.service.jmx.handler.notification;

import org.jolokia.server.core.service.notification.Client;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 20.03.13
 */
public class ClientTest {

    @Test
    public void testId() throws Exception {
        String id = "12";
        Client client = new Client(id);
        assertEquals(client.getId(),id);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*not_there.*")
    public void testInvalidHandle() throws Exception {
        String id = "12";
        Client client = new Client(id);

        client.get("not_there");
    }
}
