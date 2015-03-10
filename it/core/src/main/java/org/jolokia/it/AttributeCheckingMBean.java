package org.jolokia.it;

import java.io.File;
import java.util.*;

import javax.management.ObjectName;

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
public interface AttributeCheckingMBean {

    void reset();

    boolean getState();

    String getString();

    String getName();

    void setName(String name);

    String getNull();

    long getBytes();

    long getMemoryUsed();

    long getMemoryMax();
    
    float getLongSeconds();

    double getSmallMinutes();

    String[] getStringArray();

    PojoBean[] createLargeArray(int nr);

    List<List<PojoBean>> createLargeList(int nr);

    void setStringArray(String[] array);

    int getIntValue();

    void setIntValue(int pValue);

    File getFile();

    void setFile(File pFile);

    ObjectName getObjectName();

    void setObjectName(ObjectName objectName);

    List getList();

    void setList(List list);

    Map getMap();

    void setMap(Map map);

    Set getSet();

    void setSet(Set set);

    Map getComplexNestedValue();

    void setComplexNestedValue(Map map);

    Object getBean();

    void setBean(Object object);

    Date getDate();

    void setDate(Date pDate);

    void setWriteOnlyString(String pString);

    double getDoubleValueMin();

    double getDoubleValueMax();


    String getUtf8Content();

    Chili getChili();

    void setChili(Chili chili);

}
