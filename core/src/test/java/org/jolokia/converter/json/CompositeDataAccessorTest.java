package org.jolokia.converter.json;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import javax.management.*;
import javax.management.openmbean.OpenDataException;

import org.jolokia.converter.util.CompositeTypeAndJson;
import org.jolokia.core.service.serializer.ValueFaultHandler;
import org.jolokia.json.JSONObject;
import org.testng.annotations.Test;

import static javax.management.openmbean.SimpleType.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 04.06.14
 */
public class CompositeDataAccessorTest extends AbstractObjectAccessorTest {

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void noSettingAllowed() throws InvocationTargetException, IllegalAccessException {
        assertFalse(objectAccessor.canSetValue());
        objectAccessor.setObjectValue(objectToObjectConverter,new File("test"),"executable",Boolean.TRUE);
    }

    @Test
    public void simple() throws OpenDataException, AttributeNotFoundException, MalformedObjectNameException {
        CompositeTypeAndJson ctj = getTestData();
        JSONObject result = (JSONObject) extractJson(ctj.getCompositeData());
        assertEquals(result.size(), 4);
        assertEquals(result.get("verein"),"FCN");
        assertEquals(result.get("platz"),17L);
        assertTrue((Boolean) result.get("absteiger"));
    }

    @Test
    public void withPath() throws MalformedObjectNameException, OpenDataException, AttributeNotFoundException {
        CompositeTypeAndJson ctj = getTestData();
        String result = (String) extractJson(ctj.getCompositeData(),"complex","domain");
        assertEquals(result,"java.lang");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void withInvalidPath1() throws MalformedObjectNameException, OpenDataException, AttributeNotFoundException {
        CompositeTypeAndJson ctj = getTestData();
        extractJson(ctj.getCompositeData(),"blub");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void withInvalidPath2() throws MalformedObjectNameException, OpenDataException, AttributeNotFoundException {
        CompositeTypeAndJson ctj = getTestData();
        extractJson(ctj.getCompositeData(),"complex","domain","blub");
    }

    @Test
    public void withWildCardPath() throws MalformedObjectNameException, OpenDataException, AttributeNotFoundException {
        CompositeTypeAndJson ctj = getTestData();
        JSONObject result = (JSONObject) extractJson(ctj.getCompositeData(),null,"domain");
        assertEquals(result.size(),1);
        assertEquals(result.get("complex"),"java.lang");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void withWildCardPathAndInvalidFilter() throws MalformedObjectNameException, OpenDataException, AttributeNotFoundException {
        CompositeTypeAndJson ctj = getTestData();
        extractJson(ctj.getCompositeData(),null,"unknownAttribute");
    }


    private CompositeTypeAndJson getTestData() throws OpenDataException, MalformedObjectNameException {
        return new CompositeTypeAndJson(
                    STRING,"verein","FCN",
                    INTEGER,"platz",17,
                    BOOLEAN,"absteiger",Boolean.TRUE,
                    OBJECTNAME,"complex",new ObjectName("java.lang:type=Memory")
            );
    }

    @Override
    org.jolokia.converter.json.ObjectAccessor createExtractor() {
        return new CompositeDataAccessor();
    }


}
