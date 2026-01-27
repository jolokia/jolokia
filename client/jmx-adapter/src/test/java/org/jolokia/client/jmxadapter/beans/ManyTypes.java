/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.client.jmxadapter.beans;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

public class ManyTypes implements ManyTypesMBean {

    @Override
    public void getVoid() {

    }

    @Override
    public boolean getPrimitiveBoolean() {
        return false;
    }

    @Override
    public Boolean getBoolean() {
        return Boolean.FALSE;
    }

    @Override
    public char getPrimitiveCharacter() {
        return 0;
    }

    @Override
    public Character getCharacter() {
        return 'a';
    }

    @Override
    public byte getPrimitiveByte() {
        return 0;
    }

    @Override
    public Byte getByte() {
        return 0;
    }

    @Override
    public short getPrimitiveShort() {
        return 0;
    }

    @Override
    public Short getShort() {
        return 0;
    }

    @Override
    public int getPrimitiveInteger() {
        return 0;
    }

    @Override
    public Integer getInteger() {
        return 0;
    }

    @Override
    public long getPrimitiveLong() {
        return 0;
    }

    @Override
    public Long getLong() {
        return 0L;
    }

    @Override
    public float getPrimitiveFloat() {
        return 0f;
    }

    @Override
    public Float getFloat() {
        return 0f;
    }

    @Override
    public double getPrimitiveDouble() {
        return 0;
    }

    @Override
    public Double getDouble() {
        return 0.0;
    }

    @Override
    public String getString() {
        return "string";
    }

    @Override
    public boolean[] getPrimitiveBooleanArray() {
        return new boolean[0];
    }

    @Override
    public Boolean[] getBooleanArray() {
        return new Boolean[0];
    }

    @Override
    public char[] getPrimitiveCharacterArray() {
        return new char[0];
    }

    @Override
    public Character[] getCharacterArray() {
        return new Character[0];
    }

    @Override
    public byte[] getPrimitiveByteArray() {
        return new byte[] { 0x42 };
    }

    @Override
    public Byte[] getByteArray() {
        return new Byte[] { 0x42 };
    }

    @Override
    public short[] getPrimitiveShortArray() {
        return new short[0];
    }

    @Override
    public Short[] getShortArray() {
        return new Short[0];
    }

    @Override
    public int[] getPrimitiveIntegerArray() {
        return new int[0];
    }

    @Override
    public Integer[] getIntegerArray() {
        return new Integer[0];
    }

    @Override
    public long[] getPrimitiveLongArray() {
        return new long[0];
    }

    @Override
    public Long[] getLongArray() {
        return new Long[0];
    }

    @Override
    public float[] getPrimitiveFloatArray() {
        return new float[0];
    }

    @Override
    public Float[] getFloatArray() {
        return new Float[0];
    }

    @Override
    public double[] getPrimitiveDoubleArray() {
        return new double[0];
    }

    @Override
    public Double[] getDoubleArray() {
        return new Double[0];
    }

    @Override
    public String[] getStringArray() {
        return new String[0];
    }

    @Override
    public ObjectName getObjectName() {
        try {
            return ObjectName.getInstance("jolokia:attribute=ObjectName");
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public BigInteger getBigInteger() {
        return BigInteger.ONE;
    }

    @Override
    public BigDecimal getBigDecimal() {
        return BigDecimal.ONE;
    }

    @Override
    public Date getDate() {
        return new Date();
    }

    @Override
    public CompositeData getCompositeData() {
        return null;
    }

    @Override
    public TabularData getTabularData() {
        return null;
    }

    @Override
    public Map<String, Map<String, Integer>> getSomethingThatShouldBeOfTabularType() {
        return Map.of();
    }

    @Override
    public List<String> getList() {
        return List.of();
    }

    @Override
    public InetAddress getInetAddress() {
        try {
            return Inet4Address.getByAddress("localhost", new byte[] { (byte)127, 0, 0, 1 });
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public URI getURI() {
        return URI.create("http://jolokia.org");
    }

    @Override
    public URL getURL() {
        try {
            return new File("/tmp").toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public File getFile() {
        return new File("/tmp");
    }

    @Override
    public Class<?> getClassObject() {
        return this.getClass();
    }

    @Override
    public Set<String> getSet() {
        return Set.of("one", "and", "two");
    }

}
