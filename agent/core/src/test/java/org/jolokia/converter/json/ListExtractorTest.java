package org.jolokia.converter.json;

import java.util.Arrays;
import java.util.List;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.json.simple.JSONArray;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ListExtractorTest extends AbstractExtractorTest {


    @Test
    public void testSimple() throws Exception {
        JSONArray result = (JSONArray) extractJson(Arrays.asList("eins","zwei","drei"));
        assertEquals(result.size(), 3);
        assertEquals(result.get(0),"eins");
        assertEquals(result.get(1),"zwei");
        assertEquals(result.get(2),"drei");
    }

    @Test
    public void testWithPath() throws Exception {
        List<ObjectName> names = getObjectNameList();
        String result = (String) extractJson(names, "1", "domain");
        assertEquals(result,"test");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testWithInvalidInnerPath() throws Exception {
        List<ObjectName> names = getObjectNameList();
        extractJson(names, "1", "Yippie!");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testWithInvalidPath() throws Exception {
        List<ObjectName> names = getObjectNameList();
        extractJson(names, "4", "domain");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testWithNoNumericPath() throws Exception {
        List<ObjectName> names = getObjectNameList();
        extractJson(names, "bla");
    }


    private List<ObjectName> getObjectNameList() throws MalformedObjectNameException {
        return Arrays.asList(new ObjectName("java.lang:type=Memory"),
                             new ObjectName("test:type=blub"));
    }

    @Test
    public void testWildCardPath() throws Exception {
        List mixed = createMixedList();
        JSONArray result = (JSONArray) extractJson(mixed,null,"domain");
        assertEquals(result.size(),2);
        assertEquals(result.get(0),"java.lang");
        assertEquals(result.get(1),"test");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void testWildCardPathAndInvalidEnd() throws Exception {
        List mixed = createMixedList();
        extractJson(mixed, null, "NoNoNo!");
    }

    private List createMixedList() throws MalformedObjectNameException {
        return Arrays.asList(new ObjectName("java.lang:type=Memory"),
                             "blub",
                             null,
                             new ObjectName("test:type=blub"));
    }

    @Test
    public void testSetValue() throws Exception {
        assertTrue(extractor.canSetValue());
        List list = Arrays.asList("null", "bla");
        extractor.setObjectValue(stringToObjectConverter, list, "1", "blub");
        assertEquals(list.get(1),"blub");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSetValueInvalidIndex() throws Exception {
        List list = Arrays.asList("null", "bla");
        extractor.setObjectValue(stringToObjectConverter, list, "NaN", "blub");
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSetValueIndexOutOfBounds() throws Exception {
        List list = Arrays.asList("null", "bla");
        extractor.setObjectValue(stringToObjectConverter, list, "3", "blub");
    }

    @Override
    Extractor createExtractor() {
        return new ListExtractor();
    }
}