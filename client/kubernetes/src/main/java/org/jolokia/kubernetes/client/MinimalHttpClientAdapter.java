package org.jolokia.kubernetes.client;

import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import javax.management.remote.JMXConnector;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.OperationSupport;
import io.fabric8.kubernetes.client.okhttp.OkHttpClientImpl;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
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
import org.json.simple.JSONObject;

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
      headers.put(AuthorizationHeaderParser.JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER,"Basic " + Base64Util
          .encode(( username + ":" + password).getBytes()));
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
  public HttpResponse execute(HttpUriRequest httpUriRequest)
      throws IOException {
    try {
      final Response response = performRequest(client, urlPath,
          extractBody(httpUriRequest), httpUriRequest.getURI().getQuery(),
          allHeaders(httpUriRequest)
      );
      return convertResponse(response);
    } catch (KubernetesClientException e) {
      throw new ClientProtocolException(e);
    }

  }

  private Map<String, String> allHeaders(HttpUriRequest httpUriRequest) {
    Map<String, String> headers = new HashMap<String, String>();
    for (Header header : httpUriRequest.getAllHeaders()) {
      headers.put(header.getName(), header.getValue());
    }
    authenticate(headers, this.user, this.password);

    return headers;

  }


  public static Response performRequest(KubernetesClient client, String path, byte[] body,
      String query, Map<String, String> headers) throws IOException {
    final Builder requestBuilder = new Builder()
        .post(RequestBody.create(MediaType.parse("application/json"), body)).url(
            buildHttpUri(client, path, query));
    for (Map.Entry<String, String> header : headers.entrySet()) {
      requestBuilder.addHeader(header.getKey(), header.getValue());
    }
    io.fabric8.kubernetes.client.http.HttpClient k8sHttpClient = client.getHttpClient();
    OkHttpClient okHttpClient = ((OkHttpClientImpl) k8sHttpClient).getOkHttpClient();
    return okHttpClient.newCall(
        requestBuilder.build()
    ).execute();
  }

  private static URL buildHttpUri(KubernetesClient client, String resourcePath,
      String query) {
    final URL masterUrl = client.getMasterUrl();
    final HttpUrl.Builder builder = new HttpUrl.Builder().scheme(masterUrl.getProtocol())
        .host(masterUrl.getHost()).query(query);
    builder.encodedPath(resourcePath);
    if(masterUrl.getPort()!=-1) {
    	builder.port(masterUrl.getPort());
    }
    return builder.build().url();
  }

  protected HttpResponse convertResponse(Response response) throws IOException {
    final int responseCode = response.code();
    final BasicHttpResponse convertedResponse = new BasicHttpResponse(
        new BasicStatusLine(convertProtocol(response.protocol()), responseCode,
            response.message()));
    for (String header : response.headers().names()) {
      convertedResponse.setHeader(header, response.header(header));
    }

    if (response.body() != null) {
      final BasicHttpEntity responseEntity = new BasicHttpEntity();
      byte[] responseBytes=null;
      if (responseCode >= 400) {
        final JSONObject errorResponse = new JSONObject();
        Throwable syntethicException = new ClientProtocolException("Failure calling Jolokia in kubernetes");
        errorResponse.put("status", responseCode);
        try {//the payload would be a kubernetes error response
          syntethicException=new KubernetesClientException(convertResponseBody(response));
        } catch (Exception e) {
        }
        errorResponse.put("error_type", syntethicException.getClass().getName());
        errorResponse.put("error", syntethicException.getMessage());
        final StringWriter stacktrace = new StringWriter();
        syntethicException.printStackTrace(new PrintWriter(
            stacktrace));
        errorResponse.put("stacktrace", stacktrace.getBuffer().toString());
        responseBytes = errorResponse.toJSONString().getBytes();

      } else {
        responseBytes = response.body().bytes();
      }
      responseEntity.setContentLength(responseBytes.length);
      responseEntity.setContent(new ByteArrayInputStream(responseBytes));
      responseEntity.setContentType(response.header(HttpHeaders.CONTENT_TYPE));
      convertedResponse.setEntity(responseEntity);
      convertedResponse.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(responseBytes.length));

    }
    return convertedResponse;
  }

  private Status convertResponseBody(Response response) {
    // see io.fabric8.kubernetes.client.dsl.internal.OperationSupport.createStatus()

    String statusMessage = "";
    ResponseBody body = response != null ? response.body() : null;
    int statusCode = response != null ? response.code() : 0;
    try {
      if (body != null) {
        statusMessage = body.string();
      } else {
        statusMessage = response.message();
      }
      Status status = serialization.unmarshal(statusMessage, Status.class);
      if (status.getCode() == null) {
        status = new StatusBuilder(status).withCode(statusCode).build();
      }
      return status;
    } catch (IOException e) {
      return OperationSupport.createStatus(statusCode, statusMessage);
    }
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


  private ProtocolVersion convertProtocol(Protocol protocol) {
    final StringTokenizer parser = new StringTokenizer(protocol.name(), "_", false);
    return new ProtocolVersion(parser.nextToken(), Integer.parseInt(parser.nextToken()),
        parser.hasMoreTokens() ? Integer.parseInt(parser.nextToken()) : 0);
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
