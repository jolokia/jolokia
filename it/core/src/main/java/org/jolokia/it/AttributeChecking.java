package org.jolokia.it;

import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.management.*;

/*
 * jmx4perl - WAR Agent for exporting JMX via JSON
 *
 * Copyright (C) 2009 Roland Huß, roland@cpan.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * A commercial license is available as well. Please contact roland@cpan.org for
 * further details.
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

    public AttributeChecking() {
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
        return new ObjectName("jolokia.it:type=attribute");
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
