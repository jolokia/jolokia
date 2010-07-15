package org.jolokia.it;

import java.io.File;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

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
public interface AttributeCheckingMBean {

    void reset();

    boolean getState();

    String getString();

    String getNull();

    long getBytes();

    float getLongSeconds();

    double getSmallMinutes();

    String[] getStringArray();

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

    Map getComplexNestedValue();

    void setComplexNestedValue(Map map);

    Object getBean();

    void setBean(Object object);


}
