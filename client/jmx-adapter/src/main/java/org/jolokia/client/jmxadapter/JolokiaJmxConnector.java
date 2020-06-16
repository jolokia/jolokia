package org.jolokia.client.jmxadapter;

import java.util.HashMap;
import org.jolokia.client.J4pClientBuilder;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Map;

/**
 * Handle MBeanServerConnection using the jolokia protocol
 */
public class JolokiaJmxConnector implements JMXConnector {

  protected final JMXServiceURL serviceUrl;
  private final Map<String, ?> environment;
  protected RemoteJmxAdapter adapter;
  private final NotificationBroadcasterSupport broadcasterSupport = new NotificationBroadcasterSupport();
  private long clientNotifSeqNo = 1L;
  private String connectionId;

  public JolokiaJmxConnector(JMXServiceURL serviceURL, Map<String, ?> environment) {
    this.serviceUrl = serviceURL;
    this.environment = environment;
  }

  @Override
  public void connect() throws IOException {
    connect(Collections.<String, Object>emptyMap());
  }

  private String prefixWithSlashIfNone(String urlPath) {
    if (urlPath.startsWith("/")) {
      return urlPath;
    } else {
      return "/" + urlPath;
    }
  }

  @Override
  @SuppressWarnings({"raw"})
  public void connect(Map<String, ?> env) throws IOException {
    if (!"jolokia".equals(this.serviceUrl.getProtocol())) {
      throw new MalformedURLException(String.format("Invalid URL %s : Only protocol \"jolokia\" is supported (not %s)",  this.serviceUrl, this.serviceUrl.getProtocol()));
    }
    Map<String, Object> mergedEnv = mergedEnvironment(env);
    String internalProtocol = "http";
    if (String.valueOf(this.serviceUrl.getPort()).endsWith("443") || "true"
        .equals(mergedEnv.get("jmx.remote.x.check.stub"))) {
      internalProtocol = "https";
    }
    final J4pClientBuilder clientBuilder = new J4pClientBuilder().url(
        internalProtocol + "://" + this.serviceUrl.getHost() + ":" + this.serviceUrl.getPort()
            + prefixWithSlashIfNone(this.serviceUrl.getURLPath()));
    if (mergedEnv.containsKey(CREDENTIALS)) {
      String[] credentials = (String[]) mergedEnv.get(CREDENTIALS);
      clientBuilder.user(credentials[0]);
      clientBuilder.password(credentials[1]);
    }
    this.adapter = instantiateAdapter(clientBuilder, mergedEnv);
    postCreateAdapter();
  }

  protected void postCreateAdapter() {
    this.connectionId = this.adapter.getId();
    this.broadcasterSupport
        .sendNotification(new JMXConnectionNotification(JMXConnectionNotification.OPENED,
            this,
            this.connectionId,
            this.clientNotifSeqNo++,
            "Successful connection",
            null));
  }

  protected Map<String, Object> mergedEnvironment(Map<String, ?> env) {
    Map<String, Object> mergedEnv = new HashMap<String, Object>();
    if (this.environment != null) {
      mergedEnv.putAll(this.environment);
    }
    if (env != null) {
      mergedEnv.putAll(env);
    }
    return mergedEnv;
  }

  protected RemoteJmxAdapter instantiateAdapter(J4pClientBuilder clientBuilder,
      Map<String, Object> mergedEnv) throws IOException {
    return new RemoteJmxAdapter(clientBuilder.build());
  }

  @Override
  public MBeanServerConnection getMBeanServerConnection() {
    return this.adapter;
  }

  @Override
  public MBeanServerConnection getMBeanServerConnection(Subject delegationSubject) {
    throw new UnsupportedOperationException(
        "Jolokia currently do not support connections using a subject, if you have a use case, raise an issue in Jolokias github repo");
  }

  @Override
  public void close() {
    this.broadcasterSupport.sendNotification(
        new JMXConnectionNotification(JMXConnectionNotification.CLOSED,
            this,
            this.connectionId,
            clientNotifSeqNo++,
            "Client has been closed",
            null)
    );
    this.adapter = null;
  }

  @Override
  public void addConnectionNotificationListener(NotificationListener listener,
      NotificationFilter filter, Object handback) {
    this.broadcasterSupport.addNotificationListener(listener, filter, handback);
  }

  @Override
  public void removeConnectionNotificationListener(NotificationListener listener)
      throws ListenerNotFoundException {
    this.broadcasterSupport.removeNotificationListener(listener);
  }

  @Override
  public void removeConnectionNotificationListener(NotificationListener l, NotificationFilter f,
      Object handback) throws ListenerNotFoundException {
    this.broadcasterSupport.removeNotificationListener(l, f, handback);
  }

  @Override
  public String getConnectionId() {
    return this.connectionId;
  }
}
