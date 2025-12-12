package org.jolokia.it.core;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.*;

import javax.management.*;

/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * @author roland
 * @since Aug 7, 2009
 */
@SuppressWarnings("rawtypes")
public class AttributeChecking implements AttributeCheckingMBean,MBeanRegistration {


    private boolean state     = false;
    private int     idx       = 0;
    private String  name      = null;
    private String[] strings = {
            "Started",
            "Stopped"
    };

    private int intValue = 0;
    private File       file;
    private File       origFile;
    private ObjectName objectName;
    private List       list;
    private Map        complexMap;
    private Map        map;
    private Set        set;
    private Object     bean;
    private final String domain;
    private Date date = new Date();
    private Chili chili = Chili.AJI;
    private int[][] array2D = { { 0, 0 }, { 0, 0 } };
    private Instant instant = LocalDateTime.of(
        LocalDate.of(2024, 7, 24),
        LocalTime.of(10, 11, 41, 554433221)

    ).toInstant(ZoneOffset.ofHours(0));


    public AttributeChecking(String pDomain) {
        domain = pDomain;
        reset();
    }

    @SuppressWarnings("unchecked")
    final public void reset() {
        try {
            state = false;
            idx = 0;
            intValue = 0;
            file = origFile;
            origFile = File.createTempFile("bla", ".txt");
            file = origFile;
            objectName = new ObjectName("bla:type=blub");
            list = Arrays.asList("jolokia", "habanero", 42);
            map = new HashMap();
            map.put("fcn", "meister");
            map.put("bayern", "mittelfeld");
            map.put("zahl", 10L);
            complexMap = new HashMap();
            List inner = new ArrayList();
            Map anotherInner = new HashMap();
            int[] innerInner = new int[]{42, 23};
            anotherInner.put("numbers", innerInner);
            inner.add("Bla");
            inner.add(anotherInner);
            complexMap.put("Blub",inner);
            bean = new TestBean(13,"roland");

            set = new HashSet<String>();
            set.add("jolokia");

            set.add("habanero");
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNull() {
        return null;
    }

    public long getBytes() {
        // 3.5 MB
        return 3 * 1024 * 1024 +  1024 * 512;
    }

    public long getMemoryUsed() {
        return (long) (1017.24 * 1024 * 1024);
    }

    // For https://github.com/rhuss/jmx4perl/issues/#issue/7
    public long getMemoryMax() {
        return (long) (1.99 * 1024 * 1024 * 1024);
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


    public PojoBean[] createLargeArray(int nr) {
        PojoBean[] ret = new PojoBean[nr];
        for (int i = 0; i < nr; i++) {
            ret[i] = new PojoBean("" + i,"value" + i);
        }
        return ret;
    }

    public List<List<PojoBean>> createLargeList(int nr) {
        List<List<PojoBean>> ret = new ArrayList<>(nr);
        for (int i = 0; i < nr; i++) {
            List<PojoBean> inner = new ArrayList<>(i / 10);
            for (int j = 0; j < i / 10; j++) {
                inner.add(new PojoBean("" + j, i + "."));
            }
            ret.add(inner);
        }
        return ret;
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

    public Map getNonStringKeyMap() {
        try {
            return Map.of(InetAddress.getLocalHost(), 0.9F);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public Set getSet() {
        return set;
    }

    public void setSet(Set pSet) {
        set = pSet;
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

    public Date getDate() {
        return date;
    }

    public void setDate(Date pDate) {
        date = pDate;
    }

    public void setWriteOnlyString(String pString) {
        // nothing to be done
    }

    public double getDoubleValueMin() {
        return 4.208154711E-212;
    }

    public double getDoubleValueMax() {
        return 4.208154711E+212;
    }

    public String getUtf8Content() {
        // UTF-8: E2 98 AF, Unicode: U+262F
        return "☯";
    }

    public Chili getChili() {
        return chili;
    }

    public void setChili(Chili pChili) {
        chili = pChili;
    }

    public int[][] getArray2D() {
        return array2D;
    }

    public void setArray2D(int[][] array2D) {
        this.array2D = array2D;
    }

    public Instant getInstant() {
        return instant;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    @Override
    public Map<InetAddress, Float> getCassandraOwnership() throws UnknownHostException {
        return Map.of(
            Inet4Address.getByAddress("my-host", new byte[] { (byte) (127 & 0xff), (byte) (1 & 0xff), (byte) (2 & 0xff), (byte) (3 & 0xff) }), 3.4f,
            Inet6Address.getByAddress("my-host-6", new byte[] { 1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4 }), 4.2f
        );
    }

    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        return new ObjectName(domain + ":type=attribute");
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() {
    }

    public void postDeregister() {
    }

    final static public class TestBean implements Serializable {
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
