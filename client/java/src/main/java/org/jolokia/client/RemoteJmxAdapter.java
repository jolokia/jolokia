package org.jolokia.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.QueryEval;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.loading.ClassLoaderRepository;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.exception.UncheckedJmxAdapterException;
import org.jolokia.client.request.J4pExecRequest;
import org.jolokia.client.request.J4pExecResponse;
import org.jolokia.client.request.J4pListRequest;
import org.jolokia.client.request.J4pListResponse;
import org.jolokia.client.request.J4pReadRequest;
import org.jolokia.client.request.J4pRequest;
import org.jolokia.client.request.J4pResponse;
import org.jolokia.client.request.J4pSearchRequest;
import org.jolokia.client.request.J4pSearchResponse;
import org.jolokia.client.request.J4pWriteRequest;

public class RemoteJmxAdapter implements MBeanServer, MBeanServerConnection {

  private final J4pClient connector;

  public RemoteJmxAdapter(final J4pClient connector) {
    this.connector = connector;
  }

  @Override
  public ObjectInstance createMBean(String className, ObjectName name) {
    throw new UnsupportedOperationException("createMBean not supported over Jolokia");
  }

  @Override
  public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName) {
    throw new UnsupportedOperationException("createMBean not supported over Jolokia");
  }

  @Override
  public ObjectInstance createMBean(String className, ObjectName name, Object[] params,
      String[] signature) {
    throw new UnsupportedOperationException("createMBean not supported over Jolokia");
  }

  @Override
  public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName,
      Object[] params,
      String[] signature) {
    throw new UnsupportedOperationException("createMBean not supported over Jolokia");
  }

  @Override
  public ObjectInstance registerMBean(Object object, ObjectName name)
      throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
    throw new UnsupportedOperationException("registerMBean not supported over Jolokia");
  }

  @Override
  public void unregisterMBean(ObjectName name) {
    throw new UnsupportedOperationException("unregisterMBean not supported over Jolokia");

  }

  @Override
  public ObjectInstance getObjectInstance(ObjectName name)
      throws InstanceNotFoundException {

    J4pListResponse listResponse = this
        .unwrappExecute(new J4pListRequest(name));
    return new ObjectInstance(name, listResponse.getClassName());
  }

  private List<ObjectInstance> listObjectInstances(ObjectName name) {
    final J4pListResponse listResponse = this
        .unwrappExecute(new J4pListRequest(name));
    return listResponse.getObjectInstances();
  }


  @Override
  public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
    final HashSet<ObjectInstance> result = new HashSet<ObjectInstance>();

    for (ObjectInstance instance : this.listObjectInstances(name)) {
      if (query == null || applyQuery(query, instance.getObjectName())) {
        result.add(instance);
      }
    }
    return result;
  }

  @Override
  public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {

    final HashSet<ObjectName> result = new HashSet<ObjectName>();
    //if name is null, use list instead of search
    if(name == null) {
      try {
        name=ObjectName.getInstance("");
      } catch (MalformedObjectNameException ignore) {
      }
    }

    final J4pSearchRequest j4pSearchRequest = new J4pSearchRequest(name);
    J4pSearchResponse response = unwrappExecute(j4pSearchRequest);
    final List<ObjectName> names = response.getObjectNames();


    for (ObjectName objectName : names) {
      if (query == null || applyQuery(query, objectName)) {
        result.add(objectName);
      }

    }

    return result;
  }

  private boolean applyQuery(QueryExp query, ObjectName objectName) {
    if(QueryEval.getMBeanServer() == null) {
      query.setMBeanServer(this);
    }
    try {
      return query.apply(objectName);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new UncheckedJmxAdapterException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private <RESP extends J4pResponse<REQ>, REQ extends J4pRequest> RESP unwrappExecute(REQ pRequest)
       {
    try {
      return this.connector.execute(pRequest);
    } catch (J4pException e) {
      return (RESP) unwrapException(e);
    }
  }

  private J4pResponse unwrapException(
      J4pException e)  {
    if (e.getCause() instanceof RuntimeException) {
      throw (RuntimeException) e.getCause();
    } else if (e.getCause() instanceof Error) {
      throw (Error) e.getCause();
    } else {
      throw new UncheckedJmxAdapterException(e);
    }
  }

  @Override
  public boolean isRegistered(ObjectName name) {

    return !queryNames(name, null).isEmpty();
  }

  @Override
  public Integer getMBeanCount() {
    return this.queryNames(null, null).size();
  }

  @Override
  public Object getAttribute(ObjectName name, String attribute) {
    return unwrappExecute(new J4pReadRequest(name, attribute)).getValue();
  }

  @Override
  public AttributeList getAttributes(ObjectName name, String[] attributes)
      throws InstanceNotFoundException, ReflectionException {
    return unwrappExecute(new J4pReadRequest(name, attributes)).getValue();
  }

  @Override
  public void setAttribute(ObjectName name, Attribute attribute)
      throws InstanceNotFoundException, AttributeNotFoundException, InvalidAttributeValueException {
      this.unwrappExecute(new J4pWriteRequest(name, attribute.getName(), attribute.getValue()));
  }

  @Override
  public AttributeList setAttributes(ObjectName name, AttributeList attributes)
      throws InstanceNotFoundException, ReflectionException {

    List<J4pWriteRequest> attributeWrites=new ArrayList<J4pWriteRequest>(attributes.size());
    for(Attribute attribute: attributes.asList()) {
      attributeWrites.add(new J4pWriteRequest(name, attribute.getName(), attribute.getValue()));
    }
    try {
      this.connector.execute(attributeWrites);
    } catch (J4pException e) {
      unwrapException(e);
    }

    return attributes;
  }

  @Override
  public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature)
      throws InstanceNotFoundException, MBeanException, ReflectionException {
    final J4pExecResponse response = unwrappExecute(
        new J4pExecRequest(name, operationName, params));
    return response.getValue();
  }

  @Override
  public String getDefaultDomain() {
    // Just pick the first
    return getDomains()[0];
  }

  @Override
  public String[] getDomains() {
    List<String> domains=new LinkedList<String>();
    for(final ObjectName name : this.queryNames(null, null)) {
      if(!domains.contains(name.getDomain())) {//use list to preserve ordering, if relevant
        domains.add(name.getDomain());
      }
    }
    return domains.toArray(new String[domains.size()]);
  }

  @Override
  public void addNotificationListener(ObjectName name, NotificationListener listener,
      NotificationFilter filter,
      Object handback) throws InstanceNotFoundException {
    throw new UnsupportedOperationException("addNotificationListener not supported for Jolokia");

  }

  @Override
  public void addNotificationListener(ObjectName name, ObjectName listener,
      NotificationFilter filter,
      Object handback) throws InstanceNotFoundException {
    throw new UnsupportedOperationException("addNotificationListener not supported for Jolokia");

  }

  @Override
  public void removeNotificationListener(ObjectName name, ObjectName listener)
      throws InstanceNotFoundException, ListenerNotFoundException {
		throw new UnsupportedOperationException("removeNotificationListener not supported for Jolokia");

  }

  @Override
  public void removeNotificationListener(ObjectName name, ObjectName listener,
      NotificationFilter filter,
      Object handback) throws InstanceNotFoundException, ListenerNotFoundException {
		throw new UnsupportedOperationException("removeNotificationListener not supported for Jolokia");

  }

  @Override
  public void removeNotificationListener(ObjectName name, NotificationListener listener)
      throws InstanceNotFoundException, ListenerNotFoundException {
		throw new UnsupportedOperationException("removeNotificationListener not supported for Jolokia");

  }

  @Override
  public void removeNotificationListener(ObjectName name, NotificationListener listener,
      NotificationFilter filter,
      Object handback) throws InstanceNotFoundException, ListenerNotFoundException {
		throw new UnsupportedOperationException("removeNotificationListener not supported for Jolokia");

  }

  @Override
  public MBeanInfo getMBeanInfo(ObjectName name)
      throws InstanceNotFoundException, IntrospectionException, ReflectionException {
    final J4pListResponse response = this
        .unwrappExecute(new J4pListRequest(name));
    final List<MBeanInfo> list = response.getMbeanInfoList();
    if(list.isEmpty()) {
      throw new InstanceNotFoundException(name.getCanonicalName());
    }
    return list.get(0);
  }

  @Override
  public boolean isInstanceOf(ObjectName name, String className)
      throws InstanceNotFoundException {
    try {
      return Class.forName(className).isAssignableFrom(Class.forName(getObjectInstance(name).getClassName()));
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  @Override
  public Object instantiate(String className) throws ReflectionException, MBeanException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object instantiate(String className, ObjectName loaderName)
      throws ReflectionException, MBeanException, InstanceNotFoundException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object instantiate(String className, Object[] params, String[] signature)
      throws ReflectionException, MBeanException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object instantiate(String className, ObjectName loaderName, Object[] params,
      String[] signature) throws ReflectionException, MBeanException, InstanceNotFoundException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ObjectInputStream deserialize(ObjectName name, byte[] data)
      throws InstanceNotFoundException, OperationsException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ObjectInputStream deserialize(String className, byte[] data)
      throws OperationsException, ReflectionException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ObjectInputStream deserialize(String className, ObjectName loaderName, byte[] data)
      throws InstanceNotFoundException, OperationsException, ReflectionException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ClassLoader getClassLoaderFor(ObjectName mbeanName) throws InstanceNotFoundException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ClassLoader getClassLoader(ObjectName loaderName) throws InstanceNotFoundException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ClassLoaderRepository getClassLoaderRepository() {
    throw new UnsupportedOperationException();
  }


}
