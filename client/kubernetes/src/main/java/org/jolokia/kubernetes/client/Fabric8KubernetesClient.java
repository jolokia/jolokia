package org.jolokia.kubernetes.client;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.management.remote.JMXConnector;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.internal.OperationSupport;
import io.fabric8.kubernetes.client.http.HttpRequest;
import io.fabric8.kubernetes.client.http.HttpResponse;
import okhttp3.HttpUrl;
import org.jolokia.client.HttpUtil;
import org.jolokia.client.JolokiaQueryParameter;
import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.client.exception.JolokiaException;
import org.jolokia.client.exception.JolokiaHttpException;
import org.jolokia.client.request.HttpMethod;
import org.jolokia.client.request.JolokiaRequest;
import org.jolokia.client.response.JolokiaResponse;
import org.jolokia.client.spi.HttpClientSpi;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.json.JSONStructure;
import org.jolokia.json.parser.ParseException;

/**
 * {@link HttpClientSpi} implementation that routes Jolokia requests through a fabric8
 * {@link KubernetesClient} so that requests reach a Jolokia agent via the Kubernetes
 * API server's pod proxy. All requests are sent as POST against the fixed proxy path
 * the connector was opened with - the JSON Jolokia protocol works the same way for
 * single and bulk requests.
 */
public class Fabric8KubernetesClient implements HttpClientSpi<KubernetesClient> {

    public static final String JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER = "X-jolokia-authorization";

    private final KubernetesClient client;
    private final String urlPath;
    private final String user;
    private final String password;

    public Fabric8KubernetesClient(KubernetesClient client, String urlPath, Map<String, Object> env) {
        this.client = client;
        this.urlPath = urlPath;
        String[] credentials = (String[]) env.get(JMXConnector.CREDENTIALS);
        if (credentials != null) {
            this.user = credentials[0];
            this.password = credentials[1];
        } else {
            this.user = null;
            this.password = null;
        }
    }

    static void authenticate(Map<String, String> headers, String username, String password) {
        if (username != null) {
            //use custom header as Authorization will be stripped by kubernetes proxy
            headers.put(JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER, "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes()));
        }
    }

    @Override
    public KubernetesClient getClient(Class<KubernetesClient> clientClass) {
        if (clientClass.isAssignableFrom(client.getClass())) {
            return clientClass.cast(client);
        }
        return null;
    }

    @SuppressWarnings({"java:S2326", "java:S119"}) // NOSONAR RES type parameter is unused, type names are not idiomatic
    @Override
    public <REQ extends JolokiaRequest, RES extends JolokiaResponse<REQ>>
    JSONStructure execute(REQ pRequest, HttpMethod method, Map<JolokiaQueryParameter, String> parameters,
                          JolokiaTargetConfig targetConfig) throws JolokiaException {
        JolokiaTargetConfig effectiveTarget = HttpUtil.determineTargetConfig(pRequest, targetConfig);
        JSONObject body = HttpUtil.getJsonRequestContent(pRequest, effectiveTarget);
        return send(body.toJSONString(), HttpUtil.toQueryString(parameters), pRequest.getType().getValue());
    }

    @SuppressWarnings({"java:S2326", "java:S119"})  // NOSONAR RES type parameter is unused, type names are not idiomatic
    @Override
    public <REQ extends JolokiaRequest, RES extends JolokiaResponse<REQ>>
    JSONStructure execute(List<REQ> pRequests, Map<JolokiaQueryParameter, String> parameters,
                          JolokiaTargetConfig targetConfig) throws JolokiaException {
        JSONArray bulk = new JSONArray(pRequests.size());
        for (REQ r : pRequests) {
            bulk.add(HttpUtil.getJsonRequestContent(r, HttpUtil.determineTargetConfig(r, targetConfig)));
        }
        return send(bulk.toJSONString(), HttpUtil.toQueryString(parameters), "bulk");
    }

    @Override
    public void close() {
        // KubernetesClient is owned by the caller (cached in KubernetesJmxConnector)
    }

    private JSONStructure send(String jsonBody, String query, String requestType) throws JolokiaException {
        Map<String, String> headers = new HashMap<>();
        authenticate(headers, user, password);

        HttpResponse<byte[]> kubeResp;
        try {
            kubeResp = performRequest(client, urlPath, jsonBody.getBytes(StandardCharsets.UTF_8), query, headers);
        } catch (ExecutionException | KubernetesClientException e) {
            throw new JolokiaException("Error sending " + requestType + " request: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JolokiaException("Interrupted while sending " + requestType + " request", e);
        }

        int code = kubeResp.code();
        if (code != 200) {
            throw new JolokiaHttpException("HTTP error " + code + " sending " + requestType + " Jolokia request", code);
        }

        byte[] body = kubeResp.body();
        if (body == null || body.length == 0) {
            throw new JolokiaException("No data received from the remote Jolokia Agent for " + requestType);
        }
        try {
            return HttpUtil.parseJsonResponse(new java.io.ByteArrayInputStream(body), StandardCharsets.UTF_8);
        } catch (ParseException | IOException e) {
            throw new JolokiaException("Error parsing " + requestType + " response: " + e.getMessage());
        }
    }

    public static HttpResponse<byte[]> performRequest(KubernetesClient client,
            String path, byte[] body, String query, Map<String, String> headers)
        throws ExecutionException, InterruptedException {

        final HttpRequest.Builder requestBuilder = client.getHttpClient()
            .newHttpRequestBuilder();
        requestBuilder.method("POST", "application/json", new String(body)).url(buildHttpUri(client, path, query));
        for (Map.Entry<String, String> header : headers.entrySet()) {
            requestBuilder.header(header.getKey(), header.getValue());
        }

        HttpRequest request = requestBuilder.build();
        CompletableFuture<HttpResponse<byte[]>> futureResponse = client
            .getHttpClient().sendAsync(request, byte[].class).thenApply(response -> {
                try {
                    return response;
                } catch (KubernetesClientException e) {
                    throw e;
                } catch (Exception e) {
                    throw OperationSupport.requestException(request, e);
                }
            });
        return futureResponse.get();
    }

    private static URL buildHttpUri(KubernetesClient client, String resourcePath, String query) {
        final URL masterUrl = client.getMasterUrl();
        final HttpUrl.Builder builder = new HttpUrl.Builder().scheme(masterUrl.getProtocol())
            .host(masterUrl.getHost()).query(query);
        builder.encodedPath(resourcePath);
        if (masterUrl.getPort() != -1) {
            builder.port(masterUrl.getPort());
        }
        return builder.build().url();
    }
}
