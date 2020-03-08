package org.jolokia.kubernetes.client;

import com.squareup.okhttp.Response;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.Pair;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1OwnerReference;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Service;
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
import javax.management.remote.JMXServiceURL;
import org.jolokia.client.J4pClient;
import org.jolokia.client.jmxadapter.JolokiaJmxConnector;
import org.jolokia.client.jmxadapter.RemoteJmxAdapter;

public class KubernetesJmxConnector extends JolokiaJmxConnector {

  private static Pattern POD_PATTERN = Pattern
      .compile("/api/v1/namespaces/([^/]+)/pods/([^/]+)/proxy/(.+)");
  private static Pattern SERVICE_PATTERN = Pattern
      .compile("/api/v1/namespaces/([^/]+)/services/([^/]+)/proxy/(.+)");

  public KubernetesJmxConnector(JMXServiceURL serviceURL,
      Map<String, ?> environment) {
    super(serviceURL, environment);
  }

  @Override
  public void connect(Map<String, ?> env) throws IOException {
    if (!"kubernetes".equals(this.serviceUrl.getProtocol())) {
      throw new MalformedURLException("Only Kubernetes urls are supported");
    }
    ApiClient client = getApiClient(env);

    this.adapter = createAdapter(client);
    this.postCreateAdapter();
  }

  protected RemoteJmxAdapter createAdapter(ApiClient client) throws IOException {
    return new RemoteJmxAdapter(expandAndProbeUrl(client));
  }

  protected ApiClient getApiClient(Map<String, ?> env) throws IOException {
    // file path to your KubeConfig
    final Object configPath = env != null ? env.get("kube.config.path") : null;
    String kubeConfigPath = configPath != null ? configPath.toString()
        : String.format("%s/.kube/config", System.getProperty("user.home"));

    // loading the out-of-cluster config, a kubeconfig from file-system
    return ClientBuilder
        .kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
  }

  /**
   * @return a connection if successful
   */
  protected J4pClient expandAndProbeUrl(ApiClient client) throws MalformedURLException {
    Configuration.setDefaultApiClient(client);

    CoreV1Api api = new CoreV1Api();
    String proxyPath = this.serviceUrl.getURLPath();
    try {
      if (SERVICE_PATTERN.matcher(proxyPath).matches()) {
        final Matcher matcher = SERVICE_PATTERN.matcher(proxyPath);
        if (matcher.find()) {
          String namespacePattern = matcher.group(1);
          String servicePattern = matcher.group();
          String actualNamespace = null;
          String actualName = null;
          for (final V1Service service : api
              .listServiceForAllNamespaces(null, null, false, null, null, null, null, 5, null)
              .getItems()) {

            if (service.getMetadata().getNamespace().matches(namespacePattern) && service
                .getMetadata().getName().matches(servicePattern)) {
              actualNamespace = service.getMetadata().getNamespace();
              actualName = service.getMetadata().getName();
            }
          }
          if (actualName == null || actualNamespace == null) {
            throw new MalformedURLException(
                "Coult not find service in cluster for pattern " + proxyPath);
          }
          if (!actualNamespace.equals(namespacePattern)) {
            proxyPath = proxyPath.replace(namespacePattern, actualNamespace);
          }
          if (!actualName.equals(servicePattern)) {
            proxyPath = proxyPath.replace(servicePattern, actualName);
          }
          //probe a request to this URL via proxy
          try {
            final Response response = probeProxyPath(client, proxyPath);
            if (response.isSuccessful()) {
              return new J4pClient(
                  proxyPath, new MinimalHttpClientAdapter(client, proxyPath));
            }
          } catch (IOException ignore) {
          }
          //try to fall back to finding a pod to see if we are more successful connecting to it, will be picked up from next if block if successful

          String path = matcher.group(3);
          proxyPath = findPodPathIfAnyForService(actualName, actualNamespace, path, api);


        }
      }
      if (POD_PATTERN.matcher(proxyPath).matches()) {
        final Matcher matcher = POD_PATTERN.matcher(proxyPath);
        if (matcher.find()) {
          String namespacePattern = matcher.group(1);

          String podPattern = matcher.group(2);
          String actualNamespace = null;
          String actualPodName = null;
          V1Pod actualPod = null;
          for (final V1Pod pod : api
              .listPodForAllNamespaces(null, null, false, null, null, null, null, 5, null)
              .getItems()) {

            if (pod.getMetadata().getNamespace().matches(namespacePattern) && pod.getMetadata()
                .getName().matches(podPattern)) {
              actualNamespace = pod.getMetadata().getNamespace();
              actualPodName = pod.getMetadata().getName();
              actualPod = pod;
              break;
            }
          }
          if (actualPodName == null || actualNamespace == null) {
            throw new MalformedURLException(
                "Could not find pod in cluster for pattern " + proxyPath);
          }
          if (!actualNamespace.equals(namespacePattern)) {
            proxyPath = proxyPath.replace(namespacePattern, actualNamespace);
          }
          if (!actualPodName.equals(podPattern)) {
            proxyPath = proxyPath.replace(podPattern, actualPodName);
          }
          //probe a request to this URL via proxy
          try {
            final Response response = probeProxyPath(client, proxyPath);
            if (response.isSuccessful()) {
              return new J4pClient(
                  proxyPath, new MinimalHttpClientAdapter(client, proxyPath));
            }
/*          port forward stragegy
            else if (response.code() == 403 ) {//could be proxy is not allowed try a port forward workaround instead
              String path = matcher.group(3);
              final Integer port = actualPod.getSpec().getContainers().get(0).getPorts()
                  .get(0).getContainerPort();
              final String scheme = "http";
              final Response forwardResponse = api
                  .connectGetNamespacedPodPortforwardCall(actualPodName, actualNamespace,
                      port, null, null).execute();
              if (forwardResponse.isSuccessful()) {
                //test both http and https
                return new RemoteJmxAdapter(
                    new J4pClient(String.format("%s://localhost:%d/%s", scheme, port, path)));
              }
            }*/
          } catch (IOException ignore) {
          }
        }

      }

    } catch (ApiException ignore) {
    }
    throw new

        MalformedURLException("Unable to connect to proxypath " + proxyPath);

  }

  /**
   * Find a pod of a service, fail if none
   */
  private String findPodPathIfAnyForService(String actualName, String actualNamespace, String path,
      CoreV1Api api) throws MalformedURLException {
    try {
      for (V1Pod pod : api
          .listNamespacedPod(actualNamespace, false, null, null, null, null, null, null, 5, false)
          .getItems()) {
        for (V1OwnerReference ref : pod.getMetadata().getOwnerReferences()) {
          //pod that references the service
          if (ref.getName().equals(actualName)) {
            return String
                .format("/api/v1/namespaces/%s/pods/%s/proxy/%s", actualNamespace, actualName,
                    path);
          }
        }
      }
    } catch (ApiException ignore) {
    }

    throw new MalformedURLException(String
        .format("Could not find any pod of service %s in namespace %s with path %s", actualName,
            actualNamespace, path));
  }

  /**
   */
  public static Response probeProxyPath(ApiClient client, String proxyPath)
      throws IOException, ApiException {
    return MinimalHttpClientAdapter
        .performRequest(client, proxyPath, Collections.singletonMap("type", "version"),
            Collections.<Pair>emptyList(), "POST", new HashMap<String, String>());
  }
}
