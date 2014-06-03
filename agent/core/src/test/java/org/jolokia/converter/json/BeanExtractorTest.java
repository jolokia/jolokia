package org.jolokia.converter.json;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;

import javax.management.AttributeNotFoundException;

import org.json.simple.JSONObject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 13.08.11
 */
public class BeanExtractorTest extends AbstractExtractorTest {

    private int number;
    private String text, writeOnly;
    private boolean flag;
    private Inner inner;
    private Nacked nacked;
    private Object nulli;

    private BeanExtractorTest self;
    private BeanExtractorTest hiddenSelf;

    private Inner hiddenInner;

    @BeforeMethod
    public void setupValues() {
        number = 10;
        text = "Test";
        writeOnly = "WriteOnly";
        flag = false;
        inner = new Inner("innerValue");
        nacked = new Nacked();
        nulli = null;

        self = this;
        hiddenSelf = this;

        hiddenInner = new Inner("hiddenInnerValue");
    }

    @Test
    public void simple() throws AttributeNotFoundException {
        JSONObject res = (JSONObject) extractJson(this);
        assertEquals(res.get("number"),10);
        assertEquals(res.get("text"),"Test");
        assertFalse((Boolean) res.get("flag"));
        assertEquals( ((JSONObject) res.get("inner")).get("innerText"),"innerValue");
        assertNull(res.get("nulli"));
        assertTrue(!res.containsKey("forbiddenStream"));
        assertTrue(res.containsKey("nulli"));
        assertEquals(res.get("nacked"),"nacked object");
        assertEquals(res.get("self"),"[this]");

        JSONObject inner = (JSONObject) extractJson(this,"inner");
        assertEquals(inner.get("innerText"),"innerValue");

        JSONObject innerWithWildcardPath = (JSONObject) extractJson(this,null,"innerDate");
        assertEquals(innerWithWildcardPath.size(),1);
        assertTrue((Long) ((JSONObject) innerWithWildcardPath.get("inner")).get("millis") <= new Date().getTime());

        BeanExtractorTest test = (BeanExtractorTest) extractObject(this);
        assertEquals(test,this);

        Date date = (Date) extractObject(this,"inner","innerDate");
        assertTrue(date.getTime() <= new Date().getTime());

    }

    @Test
    public void hiddenSelfTest() throws AttributeNotFoundException {
        String res = (String) extractJson(this,"hiddenSelf","text");
        assertEquals(res,"Test");

    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void unknownMethod() throws Exception {
        extractJson(this,"blablub");
    }

    @Test
    public void simplSet() throws InvocationTargetException, IllegalAccessException {
        assertTrue(extractor.canSetValue());
        String old = (String) setObject(this,"text","NewText");
        assertEquals(old,"Test");
        assertEquals(getText(),"NewText");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*setFlag.*")
    public void invalidSet() throws InvocationTargetException, IllegalAccessException {
        setObject(this,"flag",true);
    }

    @Test
    public void writeOnly() throws InvocationTargetException, IllegalAccessException {
        assertNull(setObject(this,writeOnly,"NewWriteOnly"));
        assertEquals(writeOnly,"NewWriteOnly");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*setWrongSignature.*one parameter.*")
    public void writeWithWrongSignature() throws InvocationTargetException, IllegalAccessException {
        setObject(this,"wrongSignature","bla");
    }

    // =================================================================================
    
    @Override
    Extractor createExtractor() {
        return new BeanExtractor();
    }

    public int getNumber() {
        return number;
    }

    public String getText() {
        return text;
    }

    public OutputStream forbiddenStream() {
        return new ByteArrayOutputStream();
    }

    public boolean isFlag() {
        return flag;
    }

    public void setText(String pText) {
        text = pText;
    }

    public Inner getInner() {
        return inner;
    }

    public void setInner(Inner pInner) {
        inner = pInner;
    }

    public Nacked getNacked() {
        return nacked;
    }

    public Object getNulli() {
        return nulli;
    }


    public BeanExtractorTest getSelf() {
        return self;
    }

    public BeanExtractorTest hiddenSelf() {
        return hiddenSelf;
    }

    public void setWriteOnly(String pWriteOnly) {
        writeOnly = pWriteOnly;
    }

    public void setWrongSignature(String eins, String zwei) {

    }

    private class Inner {
        private String innerText;
        private Date innerDate = new Date();

        public Inner(String pInnerValue) {
            innerText = pInnerValue;
        }

        public String getInnerText() {
            return innerText;
        }

        public Date getInnerDate() {
            return innerDate;
        }

        public void setInnerText(String pInnerText) {
            innerText = pInnerText;
        }
    }

    private class Nacked {
        @Override
        public String toString() {
            return "nacked object";
        }
    }
}
