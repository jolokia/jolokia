package org.jolokia.service.serializer.json;

import java.lang.reflect.InvocationTargetException;
import java.util.Stack;

import javax.management.AttributeNotFoundException;

import org.jolokia.service.serializer.object.StringToObjectConverter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 21.02.13
 */
public class EnumExtractorTest {

    private EnumExtractor         enumExtractor;
    private ObjectToJsonConverter converter;

    @BeforeMethod
    public void setup() {
        enumExtractor = new EnumExtractor();
        converter = new ObjectToJsonConverter(new StringToObjectConverter(), null);
        converter.setupContext();
    }

    @Test
    public void basics() {
        assertEquals(enumExtractor.getType(), Enum.class);
        assertFalse(enumExtractor.canSetValue());
    }

    @Test
    public void jsonExtract() throws AttributeNotFoundException {
        Stack stack = new Stack();
        String result = (String) enumExtractor.extractObject(converter, TestEnum.EINS,stack,true);
        assertEquals(result,"EINS");
    }

    @Test
    public void plainExtract() throws AttributeNotFoundException {
        Stack stack = new Stack();
        Object result = enumExtractor.extractObject(converter, TestEnum.EINS,stack,false);
        assertEquals(result,TestEnum.EINS);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void setValue() throws InvocationTargetException, IllegalAccessException {
        enumExtractor.setObjectValue(null,null,null,null);
    }

    private static enum TestEnum {
        EINS,
        ZWEI;
    }
}
