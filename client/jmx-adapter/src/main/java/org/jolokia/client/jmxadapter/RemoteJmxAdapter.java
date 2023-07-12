package org.jolokia.client.jmxadapter;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryEval;
import javax.management.QueryExp;
import javax.management.RuntimeMBeanException;
import javax.management.openmbean.InvalidOpenTypeException;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import org.jolokia.client.J4pClient;
import org.jolokia.client.J4pClientBuilder;
import org.jolokia.client.exception.J4pBulkRemoteException;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.exception.J4pRemoteException;
import org.jolokia.client.exception.UncheckedJmxAdapterException;
import org.jolokia.client.request.J4pExecRequest;
import org.jolokia.client.request.J4pExecResponse;
import org.jolokia.client.request.J4pListRequest;
import org.jolokia.client.request.J4pListResponse;
import org.jolokia.client.request.J4pQueryParameter;
import org.jolokia.client.request.J4pReadRequest;
import org.jolokia.client.request.J4pReadResponse;
import org.jolokia.client.request.J4pRequest;
import org.jolokia.client.request.J4pResponse;
import org.jolokia.client.request.J4pSearchRequest;
import org.jolokia.client.request.J4pSearchResponse;
import org.jolokia.client.request.J4pVersionRequest;
import org.jolokia.client.request.J4pVersionResponse;
import org.jolokia.client.request.J4pWriteRequest;
import org.jolokia.converter.Converters;
import org.jolokia.util.ClassUtil;
import org.json.simple.JSONObject;

/**
 * I emulate a subset of the functionality of a native MBeanServerConnector but over a Jolokia
 * connection to the VM , the response types and thrown exceptions attempts to mimic as close as
 * possible the ones from a native Java MBeanServerConnection
 * <p>
 * Operations that are not supported will throw an #UnsupportedOperationException
 */
public class RemoteJmxAdapter implements MBeanServerConnection {

  private final J4pClient connector;
  private String agentId;
  private HashMap<J4pQueryParameter, String> defaultProcessingOptions;
  protected final Map<ObjectName, MBeanInfo> mbeanInfoCache = new HashMap<ObjectName, MBeanInfo>();
  String agentVersion;
  String protocolVersion;

  public RemoteJmxAdapter(final J4pClient connector) throws IOException {
    this.connector = connector;
    try {
      J4pVersionResponse response = this.unwrapExecute(new J4pVersionRequest());
      this.agentVersion = response.getAgentVersion();
      this.protocolVersion = response.getProtocolVersion();
      JSONObject value = response.getValue();
      JSONObject config = (JSONObject) value.get("config");
      this.agentId = String.valueOf(config.get("agentId"));
    } catch (InstanceNotFoundException ignore) {
    }
  }

  public int hashCode() {
    return this.connector.getUri().hashCode();
  }

  public boolean equals(Object o) {
    //as long as we refer to the same agent, we may be seen as equivalent
    return o instanceof RemoteJmxAdapter && this.connector.getUri()
        .equals(((RemoteJmxAdapter) o).connector.getUri());
  }

  @SuppressWarnings("unused")
  public RemoteJmxAdapter(final String url) throws IOException {
    this(new J4pClientBuilder().url(url).build());
  }


  @Override
  public ObjectInstance createMBean(String className, ObjectName name) {
    throw new UnsupportedOperationException("createMBean not supported by Jolokia");
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

    J4pListResponse listResponse = this.unwrapExecute(new J4pListRequest(name));
    return new ObjectInstance(name, listResponse.getClassName());
  }

  private List<ObjectInstance> listObjectInstances(ObjectName name) throws IOException {
    J4pListRequest listRequest = new J4pListRequest((String) null);
    if (name != null) {
      listRequest = new J4pListRequest(name);
    }
    try {
      final J4pListResponse listResponse = this.unwrapExecute(listRequest);
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
      response = unwrapExecute(j4pSearchRequest);
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
      query.setMBeanServer(this.createStandInMbeanServerProxyForQueryEvaluation());
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
   * Query evaluation requires an MBeanServer but only to figure out objectInstance
   *
   * @return a dynamic proxy dispatching getObjectInstance back to myself
   */
  private MBeanServer createStandInMbeanServerProxyForQueryEvaluation() {
    return (MBeanServer)
        Proxy.newProxyInstance(
            this.getClass().getClassLoader(),
            new Class[]{MBeanServer.class},
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
  private <RESP extends J4pResponse<REQ>, REQ extends J4pRequest> RESP unwrapExecute(REQ pRequest)
      throws IOException, InstanceNotFoundException {
    try {
      pRequest.setPreferredHttpMethod("POST");
      return this.connector.execute(pRequest, defaultProcessingOptions());
    } catch (J4pException e) {
      return (RESP) unwrapException(e);
    }
  }

  private Map<J4pQueryParameter, String> defaultProcessingOptions() {
    if (this.defaultProcessingOptions == null) {
      this.defaultProcessingOptions = new HashMap<J4pQueryParameter, String>();
      defaultProcessingOptions.put(J4pQueryParameter.MAX_DEPTH, "10"); // in case of stack overflow
      defaultProcessingOptions.put(J4pQueryParameter.SERIALIZE_EXCEPTION, "true");
    }
    return defaultProcessingOptions;
  }

  private static final Set<String> UNCHECKED_REMOTE_EXCEPTIONS =
      Collections.singleton("java.lang.UnsupportedOperationException");

  @SuppressWarnings("rawtypes")
  protected J4pResponse unwrapException(J4pException e)
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
      final Object rawValue = unwrapExecute(new J4pReadRequest(name, attribute)).getValue();
      return adaptJsonToOptimalResponseValue(name, attribute, rawValue, getAttributeTypeFromMBeanInfo(name, attribute));
    } catch (UncheckedJmxAdapterException e) {
      if (e.getCause() instanceof J4pRemoteException
          && "javax.management.AttributeNotFoundException"
          .equals(((J4pRemoteException) e.getCause()).getErrorType())) {
        throw new AttributeNotFoundException((e.getCause().getMessage()));
      } else {
        throw e;
      }
    } catch (UnsupportedOperationException e) {
      //JConsole does not seem to like unsupported operation while looking up attributes
      throw new AttributeNotFoundException();
    }
  }

  private Object adaptJsonToOptimalResponseValue(
      ObjectName name, String attribute, Object rawValue, String typeFromMBeanInfo)
      throws IOException, InstanceNotFoundException {
    final String qualifiedName = name + "." + attribute;

    //cache MBeanInfo (and thereby attribute types) if it is not yet cached
    getMBeanInfo(name);

    //adjust numeric types, to avoid ClassCastException e.g. in JConsole proxies
    if (this.isPrimitive(rawValue)) {
      OpenType<?> attributeType = null;
      try {
        attributeType = ToOpenTypeConverter.cachedType(qualifiedName);
      } catch (OpenDataException ignore) {
      }

      if (rawValue instanceof Number && attributeType != null) {
        return new Converters().getToOpenTypeConverter()
            .convertToObject(attributeType, rawValue);
      } else {
        return rawValue;
      }
    }

    // special case, if the attribute is ObjectName
    if (rawValue instanceof JSONObject
        && ((JSONObject) rawValue).size() == 1
        && ((JSONObject) rawValue).containsKey("objectName")) {
      return getObjectName("" + ((JSONObject) rawValue).get("objectName"));
    }
    try {
      return ToOpenTypeConverter.returnOpenTypedValue(qualifiedName, rawValue, typeFromMBeanInfo);
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
    AttributeList result = new AttributeList();

    List<J4pReadRequest> requests = new ArrayList<J4pReadRequest>(attributes.length);

    for (String attribute : attributes) {
      requests.add(new J4pReadRequest(name, attribute));
    }
    List responses = Collections.emptyList();
    try {
      responses = this.connector.execute(requests, this.defaultProcessingOptions());

    } catch (J4pBulkRemoteException e) {
      responses = e.getResults();
    } catch (J4pException ignore) {
      //will result in empty return
    }
    for (Object item : responses) {
      if (item instanceof J4pReadResponse) {
        J4pReadResponse value = (J4pReadResponse) item;
        final String attribute = value.getRequest().getAttribute();
        result.add(new Attribute(attribute,
            adaptJsonToOptimalResponseValue(name, attribute, value.getValue(), getAttributeTypeFromMBeanInfo(name, attribute))));
      }
    }
    return result;
  }

  @Override
  public void setAttribute(ObjectName name, Attribute attribute)
      throws InstanceNotFoundException, AttributeNotFoundException, InvalidAttributeValueException,
      IOException {
    final J4pWriteRequest request =
        new J4pWriteRequest(name, attribute.getName(), attribute.getValue());
    try {
      this.unwrapExecute(request);
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
    //jvisualvm may send null for no parameters
    if (params == null && (signature == null || signature.length == 0)) {
      params = new Object[0];
    }
    try {
      final J4pExecResponse response =
          unwrapExecute(
              new J4pExecRequest(name, operationName + makeSignature(signature), params));
      return adaptJsonToOptimalResponseValue(name, operationName, response.getValue(), getOperationTypeFromMBeanInfo(name, operationName, signature));
    } catch (UncheckedJmxAdapterException e) {
      if (e.getCause() instanceof J4pRemoteException) {
        throw new MBeanException((Exception) e.getCause());
      }
      throw e;
    }
  }

  private String getOperationTypeFromMBeanInfo(ObjectName name, String operationName, String[] signature)
      throws IOException, InstanceNotFoundException {
    final MBeanInfo mBeanInfo = getMBeanInfo(name);
    if (signature == null) {
      signature = new String[0];
    }
    for (final MBeanOperationInfo operation : mBeanInfo.getOperations()) {
      if (operationName.equals(operation.getName()) && Arrays
          .equals(operation.getSignature(), signature)) {
        return operation.getReturnType();
      }
    }
    return null;
  }
  private String makeSignature(String[] signature) {
    StringBuilder builder = new StringBuilder("(");
    if(signature != null) {
      for (int i = 0; i < signature.length; i++) {
        if (i > 0) {
          builder.append(',');
        }
        builder.append(signature[i]);
      }
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
    if (!isRunningInJConsole()
        && !isRunningInJVisualVm()
        && !isRunningInJmc()) {//just ignore in JConsole/JvisualVM as it wrecks the MBean page
      throw new UnsupportedOperationException("addNotificationListener not supported for Jolokia");
    }
  }

  private boolean isRunningInJVisualVm() {
    final String version = System.getProperty("netbeans.productversion");
    return version != null && version.contains("VisualVM");
  }

  private boolean isRunningInJConsole() {
    return System.getProperty("jconsole.showOutputViewer") != null;
  }

  private boolean isRunningInJmc() {
    return System.getProperty("running.in.jmc") != null;
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
    throw new UnsupportedOperationException("removeNotificationListener not supported by Jolokia");
  }

  @Override
  public void removeNotificationListener(ObjectName name, NotificationListener listener) {
    if(!isRunningInJmc() && !isRunningInJConsole() && !isRunningInJVisualVm()) {
      throw new UnsupportedOperationException("removeNotificationListener not supported by Jolokia");
    }
  }

  @Override
  public void removeNotificationListener(
      ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) {
    throw new UnsupportedOperationException("removeNotificationListener not supported by Jolokia");
  }

  @Override
  public MBeanInfo getMBeanInfo(ObjectName name) throws InstanceNotFoundException, IOException {
    MBeanInfo result = this.mbeanInfoCache.get(name);
    //cache in case client queries a lot for MBean info
    if (result == null) {
      final J4pListResponse response = this.unwrapExecute(new J4pListRequest(name));
      result = response.getMbeanInfo();
      this.mbeanInfoCache.put(name, result);
      for (MBeanAttributeInfo attr : result.getAttributes()) {
        final String qualifiedName = name + "." + attr.getName();
        try {
          if (ToOpenTypeConverter.cachedType(qualifiedName) == null) {
            ToOpenTypeConverter
                .cacheType(ToOpenTypeConverter.typeFor(attr.getType()), qualifiedName);
          }
        } catch (OpenDataException ignore) {
        } catch (InvalidOpenTypeException ignore) {
        }
      }
    }
    return result;
  }

  private String getAttributeTypeFromMBeanInfo(final ObjectName name, final String attributeName)
      throws IOException, InstanceNotFoundException {
    final MBeanInfo mBeanInfo = getMBeanInfo(name);
    for (final MBeanAttributeInfo attribute : mBeanInfo.getAttributes()) {
      if(attributeName.equals(attribute.getName())) {
        return attribute.getType();
      }
    }
    return null;
  }

  @Override
  public boolean isInstanceOf(ObjectName name, String className)
      throws InstanceNotFoundException, IOException {
    final ObjectInstance objectInstance = getObjectInstance(name);
    try {
      //try to use classes available in this VM to check compatibility
      return Class.forName(className)
          .isAssignableFrom(Class.forName(objectInstance.getClassName()));
    } catch (ClassNotFoundException e) {
      if(className.equals(objectInstance.getClassName())) {
        return true;
      } else {
        try {
          if(ToOpenTypeConverter.cachedType(name.toString())!= null) {//the proprietary Oracle VMs used to have classnames not available in newer openjdk, check for overrides
            return ToOpenTypeConverter.cachedType(name.toString()).getTypeName().equals(className);
          }
        } catch (OpenDataException ignore) {
        }
      }
      return false;
    }
  }

  String getId() {
    return this.agentId;
  }
}
