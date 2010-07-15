package org.jolokia;

import java.util.Arrays;
import java.util.List;

import javax.management.MalformedObjectNameException;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;


/**
 * @author roland
 * @since Apr 15, 2010
 */
public class JmxRequestTest {

    @Test
    public void testPathSplitting() throws MalformedObjectNameException {
        List<String> paths = JmxRequest.splitPath("hello/world");
        assertEquals(paths.size(),2);
        assertEquals(paths.get(0),"hello");
        assertEquals(paths.get(1),"world");

        paths = JmxRequest.splitPath("hello\\/world/second");
        assertEquals(paths.size(),2);
        assertEquals(paths.get(0),"hello/world");
        assertEquals(paths.get(1),"second");
    }

    @Test
    public void testPathGlueing() throws MalformedObjectNameException {
        JmxRequest req =
                new JmxRequestBuilder(JmxRequest.Type.LIST,"test:name=split").
                        build();
        req.setExtraArgs(Arrays.asList("hello/world","second"));
        String combined = req.getExtraArgsAsPath();
        assertEquals(combined,"hello\\/world/second");
    }
}
