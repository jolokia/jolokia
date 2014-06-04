package org.jolokia.converter.json;

import java.util.HashMap;
import java.util.Map;

import javax.management.ObjectName;

import org.json.simple.JSONObject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class MapExtractorTest extends AbstractExtractorTest {

    Map map;

    @BeforeMethod
    public void setUp() throws Exception {
        map = new HashMap();
        map.put("eins","one");
        map.put("zwei","second");
        map.put("drei",new ObjectName("test:type=blub"));
        map.put("vier",Boolean.TRUE);
    }

    @Test
    public void testSimple() throws Exception {
        JSONObject object = (JSONObject) extractJson(map);
        assertEquals(object.size(),4);
        assertTrue((Boolean) object.get("vier"));
    }

    @Test
    public void testWithPath() throws Exception {
        assertEquals(extractJson(map,"drei","domain"),"test");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testWithInvalidPath() throws Exception {
        extractJson(map,"fuenf");
    }

    @Test
    public void testWithWildcardPath() throws Exception {
        JSONObject result = (JSONObject) extractJson(map,null,"domain");
        assertEquals(result.size(), 1);
        assertEquals(result.get("drei"),"test");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testWithInvalidWildcardPath() throws Exception {
        extractJson(map, null, "domain2");
    }

    @Test
    public void testSetValue() throws Exception {
        assertTrue(extractor.canSetValue());
        extractor.setObjectValue(stringToObjectConverter, map, "eins", "une");
        assertEquals(map.get("eins"), "une");
    }

    @Override
    Extractor createExtractor() {
        return new MapExtractor();
    }
}