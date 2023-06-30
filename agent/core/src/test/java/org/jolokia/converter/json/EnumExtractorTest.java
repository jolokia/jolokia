package org.jolokia.converter.json;

import java.lang.reflect.InvocationTargetException;
import java.util.Stack;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.StringToObjectConverter;
import org.testng.annotations.*;

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
        converter = new ObjectToJsonConverter(new StringToObjectConverter());
        converter.setupContext();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        converter.clearContext();
    }

    @Test
    public void basics() {
        assertEquals(enumExtractor.getType(), Enum.class);
        assertFalse(enumExtractor.canSetValue());
    }

    @Test
    public void jsonExtract() throws AttributeNotFoundException {
        Stack stack = new Stack();
        assertEquals(enumExtractor.extractObject(converter, TestEnum.EINS,stack,true),"EINS");
        stack.add("EINS");
        assertEquals(enumExtractor.extractObject(converter, TestEnum.EINS,stack,true),"EINS");
    }

    @Test(expectedExceptions = AttributeNotFoundException.class)
    public void jsonExtractWithWrongPath() throws AttributeNotFoundException {
        Stack stack = new Stack();
        stack.add("ZWEI");
        enumExtractor.extractObject(converter, TestEnum.EINS,stack,true);
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
