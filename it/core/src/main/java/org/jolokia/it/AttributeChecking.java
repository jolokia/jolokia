package org.jolokia.it;

import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.management.*;

/*
 *  Copyright 2009-2010 Roland Huss
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


/**
 * @author roland
 * @since Aug 7, 2009
 */
public class AttributeChecking implements AttributeCheckingMBean,MBeanRegistration {


    private boolean state = false;
    private int idx = 0;
    private String strings[] = {
            "Started",
            "Stopped"
    };

    private int intValue = 0;

    private File file;
    private File origFile;
    private ObjectName objectName;
    private List list;
    private Map complexMap;
    private Map map;
    private Object bean;
    private String domain;

    public AttributeChecking(String pDomain) {
        domain = pDomain;
        reset();
    }

    final public void reset() {
        try {
            state = false;
            idx = 0;
            intValue = 0;
            file = origFile;
            origFile = File.createTempFile("bla",".txt");
            file = origFile;
            objectName = new ObjectName("bla:type=blub");
            list = Arrays.asList("jolokia","habanero");
            map = new HashMap();
            map.put("fcn","meister");
            map.put("bayern","mittelfeld");
            complexMap = new HashMap();
            List inner = new ArrayList();
            Map anotherInner = new HashMap();
            int innerInner[] = new int[] { 42, 23 };
            anotherInner.put("numbers",innerInner);
            inner.add("Bla");
            inner.add(anotherInner);
            complexMap.put("Blub",inner);
            bean = new TestBean(13,"roland");
        } catch (IOException e) {
            throw new RuntimeException("Couldnot create temporary file name",e);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException("Couldnot objectname",e);
        }
    }

    public boolean getState() {
        // Alternate
        state = !state;
        return state;
    }

    public String getString() {
        return strings[idx++ % 2];
    }

    public String getNull() {
        return null;
    }

    public long getBytes() {
        // 3.5 MB
        return 3 * 1024 * 1024 +  1024 * 512;
    }

    public float getLongSeconds() {
        // 2 days
        return 60*60*24*2;
    }

    public double getSmallMinutes() {
        // 10 ms
        return  1f/60 * 0.01;
    }

    public String[] getStringArray() {
        return strings;
    }

    public void setStringArray(String[] array) {
        strings = array;
    }

    public int getIntValue() {
        return intValue;
    }

    public void setIntValue(int pValue) {
        intValue = pValue;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File pFile) {
        file = pFile;
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public void setObjectName(ObjectName pObjectName) {
        objectName = pObjectName;
    }

    public List getList() {
        return list;
    }

    public void setList(List pList) {
        list = pList;
    }

    public Map getMap() {
        return map;
    }

    public void setMap(Map pMap) {
        map = pMap;
    }

    public Map getComplexNestedValue() {
        return complexMap;
    }

    public void setComplexNestedValue(Map map) {
        complexMap = map;
    }

    public Object getBean() {
        return bean;
    }

    public void setBean(Object object) {
        bean = object;
    }

    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        return new ObjectName(domain + ":type=attribute");
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
    }

    final private class TestBean {
        private int value;
        private String name;

        private TestBean(int pValue, String pName) {
            value = pValue;
            name = pName;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int pValue) {
            value = pValue;
        }

        public String getName() {
            return name;
        }

        public void setName(String pName) {
            name = pName;
        }
    }
}
