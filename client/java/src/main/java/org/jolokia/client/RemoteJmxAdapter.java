package org.jolokia.client;

import org.jolokia.client.exception.J4pException;
import org.jolokia.client.exception.J4pRemoteException;
import org.jolokia.client.exception.UncheckedJmxAdapterException;
import org.jolokia.client.request.*;
import org.jolokia.util.ClassUtil;
import org.json.simple.JSONObject;

import javax.management.*;
import javax.management.openmbean.OpenDataException;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * I emulate a subset of the functionality of a native MBeanServerConnector but over a Jolokia
 * connection to the VM , the response types and thrown exceptions
 * attempts to mimic as close as possible the ones from a native
 * Java MBeanServerConnection
 *
 * Operations that are not supported will throw an #UnsupportedOperationException
 */
public class RemoteJmxAdapter implements MBeanServerConnection {

  private final J4pClient connector;

  public RemoteJmxAdapter(final J4pClient connector) {
    this.connector = connector;
  }

  public RemoteJmxAdapter(final String url) {
    this(new J4pClientBuilder().url(url).build());
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
  public ObjectInstance createMBean(
      String className, ObjectName name, Object[] params, String[] signature) {
    throw new UnsupportedOperationException("createMBean not supported over Jolokia");
  }

  @Override
  public ObjectInstance createMBean(
      String className,
      ObjectName name,
      ObjectName loaderName,
      Object[] params,
      String[] signature) {
    throw new UnsupportedOperationException("createMBean not supported over Jolokia");
  }

  @Override
  public void unregisterMBean(ObjectName name) {
    throw new UnsupportedOperationException("unregisterMBean not supported over Jolokia");
  }

  @Override
  public ObjectInstance getObjectInstance(ObjectName name)
      throws InstanceNotFoundException, IOException {

    J4pListResponse listResponse = this.unwrappExecute(new J4pListRequest(name));
    return new ObjectInstance(name, listResponse.getClassName());
  }

  private List<ObjectInstance> listObjectInstances(ObjectName name) throws IOException {
    J4pListRequest listRequest = new J4pListRequest((String) null);
    if (name != null) {
      listRequest = new J4pListRequest(name);
    }
    try {
      final J4pListResponse listResponse = this.unwrappExecute(listRequest);
      return listResponse.getObjectInstances(name);
    } catch (MalformedObjectNameException e) {
      throw new UncheckedJmxAdapterException(e);
    } catch (InstanceNotFoundException e) {
      return Collections.emptyList();
    }
  }

  @Override
  public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) throws IOException {
    final HashSet<ObjectInstance> result = new HashSet<ObjectInstance>();

    for (ObjectInstance instance : this.listObjectInstances(name)) {
      if (query == null || applyQuery(query, instance.getObjectName())) {
        result.add(instance);
      }
    }
    return result;
  }

  @Override
  public Set<ObjectName> queryNames(ObjectName name, QueryExp query) throws IOException {

    final HashSet<ObjectName> result = new HashSet<ObjectName>();
    // if name is null, use list instead of search
    if (name == null) {
      try {
        name = getObjectName("");
      } catch (UncheckedJmxAdapterException ignore) {
      }
    }

    final J4pSearchRequest j4pSearchRequest = new J4pSearchRequest(name);
    J4pSearchResponse response;
    try {
      response = unwrappExecute(j4pSearchRequest);
    } catch (InstanceNotFoundException e) {
      return Collections.emptySet();
    }
    final List<ObjectName> names = response.getObjectNames();

    for (ObjectName objectName : names) {
      if (query == null || applyQuery(query, objectName)) {
        result.add(objectName);
      }
    }

    return result;
  }

  private boolean applyQuery(QueryExp query, ObjectName objectName) {
    if (QueryEval.getMBeanServer() == null) {
      query.setMBeanServer(this.createStandinMbeanServerProxyForQueryEvaluation());
    }
    try {
      return query.apply(objectName);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new UncheckedJmxAdapterException(e);
    }
  }

  /**
   * Query envaluation requires an MBeanServer but only to figure out objectInstance
   *
   * @return a dynamic proxy dispatching getObjectInstance back to myself
   */
  private MBeanServer createStandinMbeanServerProxyForQueryEvaluation() {
    return (MBeanServer)
        Proxy.newProxyInstance(
            this.getClass().getClassLoader(),
            new Class[] {MBeanServer.class},
            new InvocationHandler() {
              @Override
              public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().contains("getObjectInstance") && args.length == 1) {
                  return getObjectInstance((ObjectName) args[0]);
                } else {
                  throw new UnsupportedOperationException(
                      "This MBeanServer proxy does not support " + method);
                }
              }
            });
  }

  @SuppressWarnings("unchecked")
  private <RESP extends J4pResponse<REQ>, REQ extends J4pRequest> RESP unwrappExecute(REQ pRequest)
      throws IOException, InstanceNotFoundException {
    try {
      pRequest.setPreferredHttpMethod("POST");
      return this.connector.execute(pRequest);
    } catch (J4pException e) {
      return (RESP) unwrapException(e);
    }
  }

  private static Set<String> UNCHECKED_REMOTE_EXCEPTIONS =
      Collections.singleton("java.lang.UnsupportedOperationException");

  private J4pResponse unwrapException(J4pException e)
      throws IOException, InstanceNotFoundException {
    if (e.getCause() instanceof IOException) {
      throw (IOException) e.getCause();
    } else if (e.getCause() instanceof RuntimeException) {
      throw (RuntimeException) e.getCause();
    } else if (e.getCause() instanceof Error) {
      throw (Error) e.getCause();
    } else if (e.getMessage()
        .matches("Error: java.lang.IllegalArgumentException : No MBean '.+' found")) {
      throw new InstanceNotFoundException();
    } else if (e instanceof J4pRemoteException
        && UNCHECKED_REMOTE_EXCEPTIONS.contains(((J4pRemoteException) e).getErrorType())) {
      throw new RuntimeMBeanException(
          (RuntimeException)
              ClassUtil.newInstance(((J4pRemoteException) e).getErrorType(), e.getMessage()));
    } else {
      throw new UncheckedJmxAdapterException(e);
    }
  }

  @Override
  public boolean isRegistered(ObjectName name) throws IOException {

    return !queryNames(name, null).isEmpty();
  }

  @Override
  public Integer getMBeanCount() throws IOException {
    return this.queryNames(null, null).size();
  }

  @Override
  public Object getAttribute(ObjectName name, String attribute)
      throws AttributeNotFoundException, InstanceNotFoundException, IOException {
    try {
      final Object rawValue = unwrappExecute(new J4pReadRequest(name, attribute)).getValue();
      return adaptJsonToOptimalResponseValue(name, attribute, rawValue);
    } catch (UncheckedJmxAdapterException e) {
      if (e.getCause() instanceof J4pRemoteException
          && "javax.management.AttributeNotFoundException"
              .equals(((J4pRemoteException) e.getCause()).getErrorType())) {
        throw new AttributeNotFoundException((e.getCause().getMessage()));
      } else {
        throw e;
      }
    }
  }

  private Object adaptJsonToOptimalResponseValue(
      ObjectName name, String attribute, Object rawValue) {
    if (this.isPrimitive(rawValue)) {
      return rawValue;
    }
    // special case, if the attribute is ObjectName
    if (rawValue instanceof JSONObject
        && ((JSONObject) rawValue).size() == 1
        && ((JSONObject) rawValue).containsKey("objectName")) {
      return getObjectName("" + ((JSONObject) rawValue).get("objectName"));
    }
    try {
      return ToOpenTypeConverter.returnOpenTypedValue(name + "." + attribute, rawValue);
    } catch (OpenDataException e) {
      return rawValue;
    }
  }

  private boolean isPrimitive(Object rawValue) {
    return rawValue == null
        || rawValue instanceof Number
        || rawValue instanceof Boolean
        || rawValue instanceof String
        || rawValue instanceof Character;
  }

  static ObjectName getObjectName(String objectName) {
    try {
      return ObjectName.getInstance(objectName);
    } catch (MalformedObjectNameException e) {
      throw new UncheckedJmxAdapterException(e);
    }
  }

  @Override
  public AttributeList getAttributes(ObjectName name, String[] attributes)
      throws InstanceNotFoundException, IOException {
    return unwrappExecute(new J4pReadRequest(name, attributes)).getValue();
  }

  @Override
  public void setAttribute(ObjectName name, Attribute attribute)
      throws InstanceNotFoundException, AttributeNotFoundException, InvalidAttributeValueException,
          IOException {
    final J4pWriteRequest request =
        new J4pWriteRequest(name, attribute.getName(), attribute.getValue());
    try {
      this.unwrappExecute(request);
    } catch (UncheckedJmxAdapterException e) {
      if (e.getCause() instanceof J4pRemoteException) {
        J4pRemoteException remote = (J4pRemoteException) e.getCause();
        if ("javax.management.AttributeNotFoundException".equals(remote.getErrorType())) {
          throw new AttributeNotFoundException((e.getCause().getMessage()));
        }
        if ("java.lang.IllegalArgumentException".equals(remote.getErrorType())
            && remote
                .getMessage()
                .matches(
                    "Error: java.lang.IllegalArgumentException : Invalid value .+ for attribute .+")) {
          throw new InvalidAttributeValueException(remote.getMessage());
        }
      }
      throw e;
    }
  }

  @Override
  public AttributeList setAttributes(ObjectName name, AttributeList attributes)
      throws InstanceNotFoundException, IOException {

    List<J4pWriteRequest> attributeWrites = new ArrayList<J4pWriteRequest>(attributes.size());
    for (Attribute attribute : attributes.asList()) {
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
      throws InstanceNotFoundException, MBeanException, IOException {
    try {
    final J4pExecResponse response =
        unwrappExecute(new J4pExecRequest(name, operationName + makeSignature(signature), params));
    return adaptJsonToOptimalResponseValue(name, operationName, response.getValue());
    } catch (UncheckedJmxAdapterException e) {
      if(e.getCause() instanceof J4pRemoteException) {
        throw new MBeanException((Exception) e.getCause());
      }
      throw e;
    }
  }

  private String makeSignature(String[] signature) {
    StringBuilder builder=new StringBuilder("(");
    for(int i=0;i<signature.length;i++) {
      if(i>0) {
        builder.append(',');
      }
      builder.append(signature[i]);
    }
    builder.append(')');
    return builder.toString();
  }

  @Override
  public String getDefaultDomain() {
    return "DefaultDomain";
  }

  @Override
  public String[] getDomains() throws IOException {
    Set<String> domains = new HashSet<String>();
    for (final ObjectName name : this.queryNames(null, null)) {
      domains.add(name.getDomain());
    }
    return domains.toArray(new String[0]);
  }

  @Override
  public void addNotificationListener(
      ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) {
    throw new UnsupportedOperationException("addNotificationListener not supported for Jolokia");
  }

  @Override
  public void addNotificationListener(
      ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) {
    throw new UnsupportedOperationException("addNotificationListener not supported for Jolokia");
  }

  @Override
  public void removeNotificationListener(ObjectName name, ObjectName listener) {
    throw new UnsupportedOperationException("removeNotificationListener not supported for Jolokia");
  }

  @Override
  public void removeNotificationListener(
      ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) {
    throw new UnsupportedOperationException("removeNotificationListener not supported for Jolokia");
  }

  @Override
  public void removeNotificationListener(ObjectName name, NotificationListener listener) {
    throw new UnsupportedOperationException("removeNotificationListener not supported for Jolokia");
  }

  @Override
  public void removeNotificationListener(
      ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) {
    throw new UnsupportedOperationException("removeNotificationListener not supported for Jolokia");
  }

  @Override
  public MBeanInfo getMBeanInfo(ObjectName name) throws InstanceNotFoundException, IOException {
    final J4pListResponse response = this.unwrappExecute(new J4pListRequest(name));
    return response.getMbeanInfoList();
  }

  @Override
  public boolean isInstanceOf(ObjectName name, String className)
      throws InstanceNotFoundException, IOException {
    try {
      return Class.forName(className)
          .isAssignableFrom(Class.forName(getObjectInstance(name).getClassName()));
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
