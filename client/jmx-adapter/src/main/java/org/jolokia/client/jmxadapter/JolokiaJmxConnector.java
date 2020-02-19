package org.jolokia.client.jmxadapter;

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
 * IN PROGRESS: Figuring out how to best handle connection strings in a JMC setup
 */
public class JolokiaJmxConnector implements JMXConnector {
  private final JMXServiceURL serviceUrl;
  private Map<String, ?> environment;
  private RemoteJmxAdapter adapter;
  private final NotificationBroadcasterSupport broadcasterSupport=new NotificationBroadcasterSupport();
  private long clientNotifSeqNo=1L;
  private String connectionId;

  public JolokiaJmxConnector(JMXServiceURL serviceURL, Map<String, ?> environment) {
    this.serviceUrl=serviceURL;
    this.environment=environment;
  }

  @Override
  public void connect() throws IOException {
    connect(Collections.<String, Object>emptyMap());
  }

  private String prefixWithSlashIfNone(String urlPath) {
    if(urlPath.startsWith("/")) {
      return urlPath;
    } else {
      return "/" + urlPath;
    }
  }

  @Override
  public void connect(Map<String, ?> env) throws IOException {
    if(!"jolokia".equals(this.serviceUrl.getProtocol())) {
      throw new MalformedURLException("I only handle Jolokia service urls");
    }

    String internalProdocol="http";
    if(String.valueOf(this.serviceUrl.getPort()).endsWith("443") || "true".equals(env.get("jmx.remote.x.check.stub"))) {
      internalProdocol="https";
    }
    final J4pClientBuilder clientBuilder = new J4pClientBuilder().url(internalProdocol + "://" + this.serviceUrl.getHost() + ":" + this.serviceUrl.getPort() + prefixWithSlashIfNone(this.serviceUrl.getURLPath()));
    if(env.containsKey(CREDENTIALS)) {
      String[] credentials= (String[]) env.get(CREDENTIALS);
      clientBuilder.user(credentials[0]);
      clientBuilder.password(credentials[1]);
    }
    this.adapter=new RemoteJmxAdapter(clientBuilder.build());
    this.connectionId=this.adapter.getId();
    this.broadcasterSupport.sendNotification(new JMXConnectionNotification(JMXConnectionNotification.OPENED,
            this,
            this.connectionId,
            this.clientNotifSeqNo++,
            "Successful connection",
            null));
  }

  @Override
  public MBeanServerConnection getMBeanServerConnection() {
    return this.adapter;
  }

  @Override
  public MBeanServerConnection getMBeanServerConnection(Subject delegationSubject) {
    return this.adapter;
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
    this.adapter=null;
  }

  @Override
  public void addConnectionNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
    this.broadcasterSupport.addNotificationListener(listener, filter, handback);
  }

  @Override
  public void removeConnectionNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
    this.broadcasterSupport.removeNotificationListener(listener);
  }

  @Override
  public void removeConnectionNotificationListener(NotificationListener l, NotificationFilter f, Object handback) throws ListenerNotFoundException {
    this.broadcasterSupport.removeNotificationListener(l,f,handback);
  }

  @Override
  public String getConnectionId() throws IOException {
    return this.connectionId;
  }
}
