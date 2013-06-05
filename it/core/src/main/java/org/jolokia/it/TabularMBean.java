package org.jolokia.it;

import java.util.Iterator;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenMBeanAttributeInfoSupport;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

/**
 * @author Dave Messink
 * @version $
 */

public class TabularMBean implements DynamicMBean
{
  private static final String[] COLUMN_NAMES = {"Column1", "Column2", "Column3"};
  private static final String[][] TABLE_DATA = {{"Value0.0", "Value0.1", "Value0.2"},
                                                {"Value1.0", "Value1.1", "Value1.2"},
                                                {"Value2.0", "Value2.1", "Value2.2"}};

  private final MBeanInfo _mbeanInfo;
  private final TabularType _table1Type;
  private final TabularType _table2Type;

  public TabularMBean() throws
      OpenDataException
  {
    MBeanAttributeInfo[] attributes = new MBeanAttributeInfo[2];

    String[] columnDescriptions = {"column one", "column two", "column three"};
    OpenType[] columnTypes = {SimpleType.STRING, SimpleType.STRING, SimpleType.STRING};
    CompositeType compositeType = new CompositeType("SensorMetric", "Sensor metric data",
                                                    COLUMN_NAMES, columnDescriptions, columnTypes);

    _table1Type = new TabularType("Table1Type", "table one type", compositeType, new String[]{"Column1"});
    attributes[0] = new OpenMBeanAttributeInfoSupport("Table1", "table one", _table1Type, true, false, false);

    _table2Type = new TabularType("Table2Type", "table two type", compositeType, new String[]{"Column1", "Column2"});
    attributes[1] = new OpenMBeanAttributeInfoSupport("Table2", "table two", _table2Type, true, false, false);

    _mbeanInfo = new MBeanInfo(getClass().getName(),
                               "tabular mbean",
                               attributes,
                               null,
                               null,
                               null);
  }

  public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException
  {
    if (attribute.equals("Table1"))
    {
      return getTableData(_table1Type);
    }
    else if (attribute.equals("Table2"))
    {
      return getTableData(_table2Type);
    }
    else
    {
      throw new AttributeNotFoundException("MBean attribute " + attribute + " not exposed for " + getClass().getName());
    }
  }

  private Object getTableData(TabularType tabularType) throws MBeanException
  {
    TabularDataSupport tableData = new TabularDataSupport(tabularType);

    for (String[] rowData : TABLE_DATA)
    {
      try
      {
        tableData.put(new CompositeDataSupport(tabularType.getRowType(), COLUMN_NAMES, rowData));
      }
      catch (OpenDataException exc)
      {
        throw new MBeanException(exc, "Error creating table data");
      }
    }

    return tableData;
  }

  public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException,
      MBeanException, ReflectionException
  {
    throw new MBeanException(
        new UnsupportedOperationException("MBean operation setAttribute is not supported for " + getClass().getName()));
  }

  public AttributeList getAttributes(String[] attributes)
  {
    AttributeList values = new AttributeList(attributes.length);

    for (String attributeName : attributes)
    {
      Object result;

      try
      {
        result = getAttribute(attributeName);
      } catch (Exception exc)
      {
        result = exc;
      }

      values.add(new Attribute(attributeName, result));
    }

    return values;
  }

  public AttributeList setAttributes(AttributeList attributes)
  {
    Iterator iterator = attributes.iterator();
    AttributeList newValues = new AttributeList(attributes.size());

    while (iterator.hasNext())
    {
      Attribute attribute = (Attribute) iterator.next();

      try
      {
        setAttribute(attribute);
      } catch (Exception exc)
      {
        attribute = new Attribute(attribute.getName(), exc);
      }

      newValues.add(attribute);
    }

    return newValues;
  }

  public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException,
      ReflectionException
  {
    throw new MBeanException(
        new UnsupportedOperationException("MBean operation " + actionName + " is not supported for " + getClass().getName()));
  }

  public MBeanInfo getMBeanInfo()
  {
    return _mbeanInfo;
  }
}
