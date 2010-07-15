package org.jolokia.client.request;

import org.apache.http.client.methods.HttpPost;
import org.jolokia.Version;
import org.jolokia.client.exception.J4pException;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author roland
 * @since Apr 26, 2010
 */
public class J4pVersionIntegrationTest extends AbstractJ4pIntegrationTest {

    @Test
    public void versionGetRequest() throws J4pException {
        J4pVersionRequest req = new J4pVersionRequest();
        J4pVersionResponse resp = j4pClient.execute(req);
        assertEquals("Proper agent version",Version.getAgentVersion(),resp.getAgentVersion());
        assertEquals("Proper protocol version",Version.getProtocolVersion(),resp.getProtocolVersion());
        assertTrue("Request timestamp",resp.getRequestDate().getTime() <= System.currentTimeMillis());

    }

    @Test
    public void versionPostRequest() throws J4pException {
        J4pVersionRequest req = new J4pVersionRequest();
        req.setPreferredHttpMethod(HttpPost.METHOD_NAME);
        J4pVersionResponse resp = (J4pVersionResponse) j4pClient.execute(req);
        assertEquals("Proper agent version",Version.getAgentVersion(),resp.getAgentVersion());
        assertEquals("Proper protocol version",Version.getProtocolVersion(),resp.getProtocolVersion());
        assertTrue("Request timestamp",resp.getRequestDate().getTime() <= System.currentTimeMillis());
    }


}
