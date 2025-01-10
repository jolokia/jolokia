package org.jolokia.kubernetes.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.management.remote.JMXConnector;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.jolokia.server.core.osgi.security.AuthorizationHeaderParser;
import org.jolokia.server.core.util.Base64Util;
import org.jolokia.json.JSONObject;

import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.internal.OperationSupport;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import okhttp3.HttpUrl;

/**
 * This is a minimum implementation of the HttpClient interface based on what is used by J4PClient
 * hence the need to adapt One HTTP client to another HTTP client API
 */
public class MinimalHttpClientAdapter implements HttpClient {

    private final KubernetesClient client;
    private final KubernetesSerialization serialization;
    private final String urlPath;
    private String user;
    private String password;

    public MinimalHttpClientAdapter(KubernetesClient client, String urlPath, Map<String, Object> env) {
        this.client = client;
        this.serialization = this.client.getKubernetesSerialization();
        this.urlPath = urlPath;
        String[] credentials = (String[]) env.get(JMXConnector.CREDENTIALS);
        if (credentials != null) {
            this.user = credentials[0];
            this.password = credentials[1];
        }
    }

    static void authenticate(Map<String, String> headers, String username, String password) {
        if (username != null) {
            //use custom header as Authorization will be stripped by kubernetes proxy
            headers.put(AuthorizationHeaderParser.JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER, "Basic " + Base64Util
                .encode((username + ":" + password).getBytes()));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public HttpParams getParams() {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("deprecation")
    public ClientConnectionManager getConnectionManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
        try {
            final io.fabric8.kubernetes.client.http.HttpResponse<byte[]> response = performRequest(client, urlPath,
                extractBody(httpUriRequest), httpUriRequest.getURI().getQuery(), allHeaders(httpUriRequest));
            return convertResponse(response);
        } catch (KubernetesClientException | ExecutionException | InterruptedException e) {
            throw new ClientProtocolException(e);
        }

	}

    private Map<String, String> allHeaders(HttpUriRequest httpUriRequest) {
        Map<String, String> headers = new HashMap<>();
        for (Header header : httpUriRequest.getAllHeaders()) {
            headers.put(header.getName(), header.getValue());
        }
        authenticate(headers, this.user, this.password);

        return headers;

    }

	public static io.fabric8.kubernetes.client.http.HttpResponse<byte[]> performRequest(KubernetesClient client,
			String path, byte[] body, String query, Map<String, String> headers)
			throws IOException, InterruptedException, ExecutionException {

		final io.fabric8.kubernetes.client.http.HttpRequest.Builder requestBuilder = client.getHttpClient()
				.newHttpRequestBuilder();
		requestBuilder.method("POST", "application/json", new String(body)).url(buildHttpUri(client, path, query));
		for (Map.Entry<String, String> header : headers.entrySet()) {
			requestBuilder.header(header.getKey(), header.getValue());
		}

		io.fabric8.kubernetes.client.http.HttpRequest request = requestBuilder.build();
		CompletableFuture<io.fabric8.kubernetes.client.http.HttpResponse<byte[]>> futureResponse = client
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

    private static URL buildHttpUri(KubernetesClient client, String resourcePath,
                                    String query) {
        final URL masterUrl = client.getMasterUrl();
        final HttpUrl.Builder builder = new HttpUrl.Builder().scheme(masterUrl.getProtocol())
            .host(masterUrl.getHost()).query(query);
        builder.encodedPath(resourcePath);
        if (masterUrl.getPort() != -1) {
            builder.port(masterUrl.getPort());
        }
        return builder.build().url();
    }

	protected HttpResponse convertResponse(io.fabric8.kubernetes.client.http.HttpResponse<byte[]> response) {
		final int responseCode = response.code();
		//NB: We have no reliable information about http protocol, so this may be misleading
		//however the Kubernetes Java client does not seem to use the version in the response for anything
		final ProtocolVersion hackHardcodedHttpVersion = new ProtocolVersion("http", 1, 1);
		final BasicHttpResponse convertedResponse = new BasicHttpResponse(
				new BasicStatusLine(hackHardcodedHttpVersion, responseCode, response.message()));
		for (String header : response.headers().keySet()) {
			convertedResponse.setHeader(header, response.header(header));
		}

        if (response.body() != null) {
            final BasicHttpEntity responseEntity = new BasicHttpEntity();
            byte[] responseBytes;
            if (responseCode >= 400) {
                final JSONObject errorResponse = new JSONObject();
                Throwable syntethicException = new ClientProtocolException("Failure calling Jolokia in kubernetes");
                errorResponse.put("status", responseCode);
                try {//the payload would be a kubernetes error response
                    syntethicException = new KubernetesClientException(convertResponseBody(response));
                } catch (Exception ignored) {
                }
                errorResponse.put("error_type", syntethicException.getClass().getName());
                errorResponse.put("error", syntethicException.getMessage());
                final StringWriter stacktrace = new StringWriter();
                syntethicException.printStackTrace(new PrintWriter(
                    stacktrace));
                errorResponse.put("stacktrace", stacktrace.getBuffer().toString());
                responseBytes = errorResponse.toJSONString().getBytes();

            } else {
                responseBytes = response.body();
            }
            responseEntity.setContentLength(responseBytes.length);
            responseEntity.setContent(new ByteArrayInputStream(responseBytes));
            responseEntity.setContentType(response.header(HttpHeaders.CONTENT_TYPE));
            convertedResponse.setEntity(responseEntity);
            convertedResponse.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(responseBytes.length));

        }
        return convertedResponse;
    }

    private Status convertResponseBody(io.fabric8.kubernetes.client.http.HttpResponse<byte[]> response) {
        // see io.fabric8.kubernetes.client.dsl.internal.OperationSupport.createStatus()

        String statusMessage = "";
        byte[] body = response != null ? response.body() : null;
        int statusCode = response != null ? response.code() : 0;

        if (body != null) {
            statusMessage = new String(body);
        } else if (response != null) {
            statusMessage = response.message();
        }
        Status status = serialization.unmarshal(statusMessage, Status.class);
        if (status.getCode() == null) {
            status = new StatusBuilder(status).withCode(statusCode).build();
        }
        return status;
    }

    protected byte[] extractBody(HttpRequest httpUriRequest) throws IOException {
        if (httpUriRequest instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest) httpUriRequest).getEntity();
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            entity.writeTo(buffer);
            return buffer.toByteArray();
        } else {
            return null;
        }
    }

    @Override
    public HttpResponse execute(HttpUriRequest httpUriRequest, HttpContext httpContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpResponse execute(HttpHost httpHost, HttpRequest httpRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpResponse execute(HttpHost httpHost, HttpRequest httpRequest, HttpContext httpContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T execute(HttpUriRequest httpUriRequest,
                         ResponseHandler<? extends T> responseHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T execute(HttpUriRequest httpUriRequest, ResponseHandler<? extends T> responseHandler,
                         HttpContext httpContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T execute(HttpHost httpHost, HttpRequest httpRequest,
                         ResponseHandler<? extends T> responseHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T execute(HttpHost httpHost, HttpRequest httpRequest,
                         ResponseHandler<? extends T> responseHandler, HttpContext httpContext) {
        throw new UnsupportedOperationException();
    }
}
