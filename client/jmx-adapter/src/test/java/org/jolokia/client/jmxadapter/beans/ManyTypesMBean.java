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
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

/**
 * MBean with attributes of many types that not necessarily are Open Types.
 */
public interface ManyTypesMBean {

    void getVoid();

    boolean getPrimitiveBoolean();
    Boolean getBoolean();

    char getPrimitiveCharacter();
    Character getCharacter();

    byte getPrimitiveByte();
    Byte getByte();
    short getPrimitiveShort();
    Short getShort();
    int getPrimitiveInteger();
    Integer getInteger();
    long getPrimitiveLong();
    Long getLong();

    float getPrimitiveFloat();
    Float getFloat();
    double getPrimitiveDouble();
    Double getDouble();

    String getString();

    boolean[] getPrimitiveBooleanArray();
    Boolean[] getBooleanArray();

    char[] getPrimitiveCharacterArray();
    Character[] getCharacterArray();

    byte[] getPrimitiveByteArray();
    Byte[] getByteArray();
    short[] getPrimitiveShortArray();
    Short[] getShortArray();
    int[] getPrimitiveIntegerArray();
    Integer[] getIntegerArray();
    long[] getPrimitiveLongArray();
    Long[] getLongArray();

    float[] getPrimitiveFloatArray();
    Float[] getFloatArray();
    double[] getPrimitiveDoubleArray();
    Double[] getDoubleArray();

    String[] getStringArray();

    ObjectName getObjectName();
    BigInteger getBigInteger();
    BigDecimal getBigDecimal();
    Date getDate();

    CompositeData getCompositeData();
    TabularData getTabularData();

    Map<String, Map<String, Integer>> getSomethingThatShouldBeOfTabularType();

    List<String> getList();

    InetAddress getInetAddress();
    URI getURI();
    URL getURL();
    File getFile();
    Class<?> getClassObject();
    Set<String> getSet();

}
