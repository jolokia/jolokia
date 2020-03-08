package org.jolokia.kubernetes.client;

import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Response;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Pair;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
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

/**
 * I implement a minimum support of the HttpClient interface
 * based on what is used by J4PClient
 */
public class MinimalHttpClientAdapter implements HttpClient {

  private final ApiClient client;
  private final String urlPath;

  public MinimalHttpClientAdapter(ApiClient client, String urlPath) {
    this.client = client;
    this.urlPath = urlPath;
  }

  @Override
  public HttpParams getParams() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ClientConnectionManager getConnectionManager() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpResponse execute(HttpUriRequest httpUriRequest)
      throws IOException, ClientProtocolException {
    Map<String,String> headers= new HashMap<String, String>();
    try {
      final Response response = performRequest(client, urlPath,
          extractBody(httpUriRequest, headers), extractQueryParameters(httpUriRequest),
          httpUriRequest.getMethod(), headers
      );
      return convertResponse(response);
    } catch (ApiException e) {
      throw new ClientProtocolException(e);
    }

  }

  public static Response performRequest(ApiClient client, String urlPath, Object body,
      List<Pair> queryParams, String method, Map<String, String> headers) throws IOException, ApiException {
    return client
              .buildCall(urlPath, method,
                  queryParams, Collections.<Pair>emptyList(),
                  body,
                  headers, Collections.<String, Object>emptyMap(),  new String[]{"BearerToken"}, null)
              .execute();
  }

  protected HttpResponse convertResponse(Response response) throws IOException {
    final BasicHttpResponse convertedResponse = new BasicHttpResponse(
        new BasicStatusLine(convertProtocol(response.protocol()), response.code(), response.message()));
    final BasicHttpEntity responseEntity = new BasicHttpEntity();
    final byte[] responseBytes = response.body().bytes();
    responseEntity.setContentLength(responseBytes.length);
    responseEntity.setContent(new ByteArrayInputStream(responseBytes));
    responseEntity.setContentType(response.header(HttpHeaders.CONTENT_TYPE));
    convertedResponse.setEntity(responseEntity);
    for(String header : response.headers().names()) {
      convertedResponse.setHeader(header, response.header(header));
    }
    return convertedResponse;
  }

  protected byte[] extractBody(HttpRequest httpUriRequest,
      Map<String, String> headers) throws IOException {
    if(httpUriRequest instanceof HttpEntityEnclosingRequest) {
      HttpEntity entity = ((HttpEntityEnclosingRequest) httpUriRequest).getEntity();
      final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      entity.writeTo(buffer);
      headers.put(HttpHeaders.CONTENT_LENGTH, String.valueOf(entity.getContentLength()));
      headers.put(HttpHeaders.CONTENT_TYPE, String.valueOf(entity.getContentType()));
      return buffer.toByteArray();
    } else {
      return null;
    }
  }

  protected List<Pair> extractQueryParameters(HttpUriRequest httpUriRequest) {
    List<Pair> queryParams=new LinkedList<Pair>();
    if(httpUriRequest.getURI().getQuery() != null) {
      final StringTokenizer tok = new StringTokenizer(
          httpUriRequest.getURI().getQuery(), "&=", false);
      while (tok.hasMoreElements()) {
        String key = tok.nextToken();
        if (tok.hasMoreElements()) {
          queryParams.add(new Pair(key, tok.nextToken()));
        }
      }
    }
    return queryParams;
  }

  private ProtocolVersion convertProtocol(Protocol protocol) {
    final StringTokenizer parser = new StringTokenizer(protocol.name(), "_", false);
    return new ProtocolVersion(parser.nextToken(), Integer.parseInt(parser.nextToken()), parser.hasMoreTokens() ? Integer.parseInt(parser.nextToken()): 0);
  }

  @Override
  public HttpResponse execute(HttpUriRequest httpUriRequest, HttpContext httpContext)
      throws IOException, ClientProtocolException {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpResponse execute(HttpHost httpHost, HttpRequest httpRequest)
      throws IOException, ClientProtocolException {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpResponse execute(HttpHost httpHost, HttpRequest httpRequest, HttpContext httpContext)
      throws IOException, ClientProtocolException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T execute(HttpUriRequest httpUriRequest, ResponseHandler<? extends T> responseHandler)
      throws IOException, ClientProtocolException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T execute(HttpUriRequest httpUriRequest, ResponseHandler<? extends T> responseHandler,
      HttpContext httpContext) throws IOException, ClientProtocolException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T execute(HttpHost httpHost, HttpRequest httpRequest,
      ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T execute(HttpHost httpHost, HttpRequest httpRequest,
      ResponseHandler<? extends T> responseHandler, HttpContext httpContext)
      throws IOException, ClientProtocolException {
    throw new UnsupportedOperationException();
  }
}
