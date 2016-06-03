package org.jolokia.service.serializer;

import com.google.gson.Gson;
import org.jolokia.server.core.service.serializer.Serializer;
import org.json.simple.JSONAware;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Objects;

import static org.jolokia.server.core.util.EscapeUtil.parsePath;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class GSonSerializerTest {

    private final Serializer seralizer = new GSonSerializer(100);
    private final String expected = "{\"someString\":\"someStringDummyValue\",\"innerBean\":{\"someString\":\"anotherStringDummyValue\",\"innerBean\":null}}";

    public static class DummyBean {
        private String someString;

        private DummyBean innerBean;

        public String getSomeString() {
            return someString;
        }

        public void setSomeString(String someString) {
            this.someString = someString;
        }

        public DummyBean getInnerBean() {
            return innerBean;
        }

        public void setInnerBean(DummyBean innerBean) {
            this.innerBean = innerBean;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            DummyBean dummyBean = (DummyBean) o;
            return Objects.equals(someString, dummyBean.someString) &&
                    Objects.equals(innerBean, dummyBean.innerBean);
        }

        @Override
        public int hashCode() {
            return Objects.hash(someString, innerBean);
        }
    }

    private DummyBean createDummyValue() {
        DummyBean dummyBean = new DummyBean();
        dummyBean.setSomeString("someStringDummyValue");
        DummyBean innerDummyBean = new DummyBean();
        innerDummyBean.setSomeString("anotherStringDummyValue");
        dummyBean.setInnerBean(innerDummyBean);
        return dummyBean;
    }

    @Test
    public void testSerializeWithoutPath() throws Exception {
        Object s = seralizer.serialize(createDummyValue(), Collections.<String>emptyList(), null);
        assertTrue(s instanceof JSONAware);
//        assertEquals(expected, s.toString());
    }

    @Test
    public void testSerializeWithPath() throws Exception {
        assertEquals(seralizer.serialize(createDummyValue(), parsePath("$.innerBean.someString"), null).toString(), "\"anotherStringDummyValue\"");
    }

    @Test
    public void testDeserialize() throws Exception {
        assertEquals(createDummyValue(), seralizer.deserialize(DummyBean.class.getName(), expected));
    }

    @Test
    public void testSetInnerValue() throws Exception {
        DummyBean newDummyBean = new DummyBean();
        newDummyBean.setSomeString("yetAnotherOne");
        DummyBean pOuterObject = createDummyValue();
        pOuterObject.setInnerBean(newDummyBean);
        DummyBean updatedByPath = (DummyBean) seralizer.setInnerValue(pOuterObject, new Gson().toJson(newDummyBean), parsePath("$.innerBean")).getUpdatedValue();
        assertEquals(pOuterObject, updatedByPath);
        assertEquals("yetAnotherOne", updatedByPath.getInnerBean().getSomeString());
    }
}