package org.jolokia.service.serializer.json;

import java.lang.reflect.InvocationTargetException;
import java.util.Deque;
import java.util.LinkedList;

import javax.management.AttributeNotFoundException;

import org.jolokia.service.serializer.object.StringToObjectConverter;
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
        Deque<String> stack = new LinkedList<>();
        assertEquals(enumExtractor.extractObject(converter, TestEnum.EINS,stack,true),"EINS");
        stack.add("EINS");
        assertEquals(enumExtractor.extractObject(converter, TestEnum.EINS,stack,true),"EINS");
    }

    @Test(expectedExceptions = AttributeNotFoundException.class)
    public void jsonExtractWithWrongPath() throws AttributeNotFoundException {
        Deque<String> stack = new LinkedList<>();
        stack.add("ZWEI");
        enumExtractor.extractObject(converter,TestEnum.EINS,stack,true);
    }

    @Test
    public void plainExtract() throws AttributeNotFoundException {
        Deque<String> stack = new LinkedList<>();
        Object result = enumExtractor.extractObject(converter, TestEnum.EINS,stack,false);
        assertEquals(result,TestEnum.EINS);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void setValue() throws InvocationTargetException, IllegalAccessException {
        enumExtractor.setObjectValue(null,null,null,null);
    }

    private enum TestEnum {
        EINS,
        ZWEI
    }
}
