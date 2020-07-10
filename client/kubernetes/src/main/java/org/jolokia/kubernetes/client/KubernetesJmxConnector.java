package org.jolokia.kubernetes.client;

import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.Response;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.Pair;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import org.jolokia.client.J4pClient;
import org.jolokia.client.jmxadapter.JolokiaJmxConnector;
import org.jolokia.client.jmxadapter.RemoteJmxAdapter;

public class KubernetesJmxConnector extends JolokiaJmxConnector {

  private static Pattern POD_PATTERN = Pattern
      .compile("/namespaces/([^/]+)/pods/([^/]+)/(.+)");
  private static ApiClient apiClient;

  public KubernetesJmxConnector(JMXServiceURL serviceURL,
      Map<String, ?> environment) {
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
    ApiClient client = getApiClient(mergedEnvironment);

    this.adapter = createAdapter(expandAndProbeUrl(client, mergedEnvironment));
    this.postCreateAdapter();
  }

  protected RemoteJmxAdapter createAdapter(J4pClient client) throws IOException {
    return new RemoteJmxAdapter(client);
  }

  public static ApiClient getApiClient(Map<String, ?> env) throws IOException {
    if (apiClient != null) {
      return apiClient;
    }
    return buildApiClient(env);
  }

  public static ApiClient buildApiClient(Map<String, ?> env) throws IOException {
    // file path to your KubeConfig
    final Object configPath = env != null ? env.get("kube.config.path") : null;
    String kubeConfigPath = configPath != null ? configPath.toString()
        : String.format("%s/.kube/config", System.getProperty("user.home"));

    // loading the out-of-cluster config, a kubeconfig from file-system
    return apiClient = ClientBuilder
        .kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
  }

  /**
   * @return a connection if successful
   */
  protected J4pClient expandAndProbeUrl(ApiClient client,
      Map<String, Object> env) throws MalformedURLException {
    Configuration.setDefaultApiClient(client);
    CoreV1Api api = new CoreV1Api();
    String proxyPath = this.serviceUrl.getURLPath();
    J4pClient connection;
    final HashMap<String, String> headersForProbe = createHeadersForProbe(env);
    try {
      if (POD_PATTERN.matcher(proxyPath).matches()) {
        final Matcher matcher = POD_PATTERN.matcher(proxyPath);
        if (matcher.find()) {
          String namespace = matcher.group(1);
          String podPattern = matcher.group(2);
          String path = matcher.group(3);
          //check if podname pans out directly
          if ((connection = probeProxyPath(env, client, namespace, podPattern, path,
              headersForProbe)) != null) {
            return connection;
          } else { //scan through pods in namespace if podname is a pattern
            for (final V1Pod pod : api
                .listNamespacedPod(namespace,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    10,
                    null)
                .getItems()) {
              if (pod.getMetadata().getNamespace().matches(namespace) && pod.getMetadata()
                  .getName().matches(podPattern)) {
                if ((connection = probeProxyPath(env, client, namespace,
                    pod.getMetadata().getName(), path, headersForProbe)) != null) {
                  return connection;
                }
              }
            }
          }
        }
      }
    } catch (ApiException ignore) {
    }
    throw new MalformedURLException("Unable to connect to proxypath " + proxyPath);
  }

  private HashMap<String, String> createHeadersForProbe(
      Map<String, Object> env) {
    final HashMap<String, String> headers = new HashMap<String, String>();
    String[] credentials = (String[]) env.get(JMXConnector.CREDENTIALS);
    if (credentials != null) {
      headers.put("Authorization", Credentials.basic(credentials[0], credentials[1]));
    }
    return headers;
  }

  /**
   * Probe whether we find Jolokia in given namespace, pod and path
   */
  public static J4pClient probeProxyPath(Map<String, Object> env, ApiClient client,
      String namespace, String podName, String path,
      HashMap<String, String> headers)
      throws ApiException {

    final String proxyPath = String
        .format("/api/v1/namespaces/%s/pods/%s/proxy/%s", namespace, podName, path);
    try {
      Response response = MinimalHttpClientAdapter
          .performRequest(client, proxyPath, Collections.singletonMap("type", "version"),
              Collections.<Pair>emptyList(), "POST", headers);

      response.body().close();
      if (response.isSuccessful()) {
        return new J4pClient(
            proxyPath, new MinimalHttpClientAdapter(client, proxyPath, env));
      }
    } catch (IOException ignore) {
    }
    return null;
  }
}
