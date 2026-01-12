package org.jolokia.kubernetes.client;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.http.HttpResponse;
import org.jolokia.client.JolokiaClient;
import org.jolokia.client.JolokiaClientBuilder;
import org.jolokia.client.httpclient4.Http4Client;
import org.jolokia.client.jmxadapter.JolokiaJmxConnector;
import org.jolokia.client.jmxadapter.RemoteJmxAdapter;

public class KubernetesJmxConnector extends JolokiaJmxConnector {

  private static final Pattern POD_PATTERN = Pattern
      .compile(
          "/?(?<namespace>[^/]+)/(?<protocol>https?:)?(?<podPattern>[^/^:]+)(?<port>:[^/]+)?/(?<path>.+)");
  private static final Map<String,KubernetesClient> apiClients = Collections.synchronizedMap(new HashMap<>());
  public static String KUBERNETES_CLIENT_CONTEXT ="kubernetes.client.context";

  public KubernetesJmxConnector(JMXServiceURL serviceURL, Map<String, ?> environment) throws IOException {
    super(serviceURL, environment);
  }

  @Override
  public void connect(Map<String, ?> env) throws IOException {
    if (!"kubernetes".equals(this.serviceUrl.getProtocol())) {
      throw new MalformedURLException(String
          .format("Invalid URL %s : Only protocol \"kubernetes\" is supported (not %s)", serviceUrl,
              serviceUrl.getProtocol()));
    }
    final Map<String, Object> mergedEnvironment = this.mergedEnvironment(env);
    KubernetesClient client = getApiClient((String) env.get(KUBERNETES_CLIENT_CONTEXT));

    this.adapter = createAdapter(expandAndProbeUrl(client, mergedEnvironment));
    this.postCreateAdapter();
  }

  protected RemoteJmxAdapter createAdapter(JolokiaClient client) throws IOException {
    return new RemoteJmxAdapter(client);
  }

  /**
   * Get a kubernetes client for the specified local context (in ~/.kube/config)
   * @param context , specify context, null for current context
   * @return client configured for the specified context - potentially recycled
   * as the setup is expensive (YAML parsing is amazingly slow)
   */
  public static KubernetesClient getApiClient(String context) {
    final String key = String.valueOf(context);
    KubernetesClient client = apiClients.get(key);

    if(client == null){
      client=new KubernetesClientBuilder().withConfig(Config.autoConfigure(context)).build();
      apiClients.put(key, client);
    }
    return client;
  }

  /**
   * Manually reset any cached config. To be uses in case you have changed your kubeconfig
   */
  public static void resetKubernetesConfig() {
    apiClients.clear();
  }

  /**
   * @return a connection if successful
   */
  protected JolokiaClient expandAndProbeUrl(KubernetesClient client,
      Map<String, Object> env) throws MalformedURLException {
    String proxyPath = this.serviceUrl.getURLPath();
      JolokiaClient connection;
    final HashMap<String, String> headersForProbe = createHeadersForProbe(env);
    try {
      if (POD_PATTERN.matcher(proxyPath).matches()) {
        final Matcher matcher = POD_PATTERN.matcher(proxyPath);
        if (matcher.find()) {
          String namespace = matcher.group("namespace");
          String podPattern = matcher.group("podPattern");
          String path = matcher.group("path");
          String protocol = matcher.group("protocol");
          String port = matcher.group("port");
          final Pod exactPod = client.pods().inNamespace(namespace).withName(podPattern).get();
          //check if podname pans out directly
          if (exactPod != null
              && (connection = probeProxyPath(env, client, buildProxyPath(exactPod, protocol, port, path),
              headersForProbe)) != null) {
            return connection;
          } else { //scan through pods in namespace if podname is a pattern

            for (final Pod pod :
                client.pods().inNamespace(namespace).list().getItems()) {
              if (pod.getMetadata()
                  .getName().matches(podPattern)) {
                if ((connection = probeProxyPath(env, client, buildProxyPath(pod, protocol, port, path),
                    headersForProbe)) != null) {
                  return connection;
                }
              }
            }
          }
        }
      }
    } catch (KubernetesClientException ignore) {
    }
    throw new MalformedURLException("Unable to connect to proxypath " + proxyPath);
  }

  public static StringBuilder buildProxyPath(Pod pod, String protocol, String port, String path) {
    final ObjectMeta metadata = pod.getMetadata();
    final StringBuilder url = new StringBuilder("/api/").append(pod.getApiVersion()).append("/namespaces/").append(metadata.getNamespace()).append("/pods/");
    if (protocol != null && !protocol.equals("http:")) {
      url.append(protocol);
    }
    url.append(metadata.getName());
    if(port!=null){
      url.append(port);
    }
    url.append("/proxy");

    if(!path.startsWith("/")) {
      url.append('/');
    }
    url.append(path);
    return url;
  }

  private static HashMap<String, String> createHeadersForProbe(
      Map<String, Object> env) {
    final HashMap<String, String> headers = new HashMap<>();
    String[] credentials = (String[]) env.get(JMXConnector.CREDENTIALS);
    if (credentials != null) {
      MinimalHttpClientAdapter.authenticate(headers, credentials[0], credentials[1]);
    }
    return headers;
  }

  /**
   * Probe whether we find Jolokia in given namespace, pod and path
   */
  public static JolokiaClient probeProxyPath(Map<String, Object> env, KubernetesClient client,
      StringBuilder url,
      HashMap<String, String> headers) {
    try {
      final String proxyPath = url.toString();
            HttpResponse<byte[]> response = MinimalHttpClientAdapter.performRequest(client,
                proxyPath,
                "{\"type\":\"version\"}".getBytes(), null, headers);
      if (response.isSuccessful()) {
          Http4Client client4 = new Http4Client(new MinimalHttpClientAdapter(client, proxyPath, env),
              JolokiaClientBuilder.Configuration.withUrl(URI.create(proxyPath)));
        return new JolokiaClient(URI.create(proxyPath), client4);
      }
    } catch (IOException | InterruptedException | ExecutionException ignore) {
    }
    return null;
  }
}
