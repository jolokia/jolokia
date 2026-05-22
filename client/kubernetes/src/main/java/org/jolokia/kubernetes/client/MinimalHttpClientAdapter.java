package org.jolokia.kubernetes.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URL;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.ResponseInfo;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import javax.management.remote.JMXConnector;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.internal.OperationSupport;
import okhttp3.HttpUrl;

/**
 * Minimum implementation of the JDK {@link HttpClient} that routes requests through a
 * fabric8 {@link KubernetesClient} instead of opening direct sockets. Intended for
 * use with {@code org.jolokia.client.jdkclient.JdkHttpClient}.
 *
 * Only the synchronous {@link #send(HttpRequest, BodyHandler)} path is implemented;
 * async send and websocket support are not used by JolokiaClient and throw UOE.
 */
public class MinimalHttpClientAdapter extends HttpClient {

    public static final String JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER = "X-jolokia-authorization";

    private final KubernetesClient client;
    private final String urlPath;
    private String user;
    private String password;

    public MinimalHttpClientAdapter(KubernetesClient client, String urlPath, Map<String, Object> env) {
        this.client = client;
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
            headers.put(JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER, "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes()));
        }
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        byte[] requestBody = drainBody(request);
        Map<String, String> headers = flattenHeaders(request);
        authenticate(headers, user, password);

        final io.fabric8.kubernetes.client.http.HttpResponse<byte[]> kubeResp;
        try {
            kubeResp = performRequest(client, urlPath, requestBody, request.uri().getQuery(), headers);
        } catch (KubernetesClientException e) {
            throw new IOException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IOException(cause);
        }

        final HttpHeaders responseHeaders = convertHeaders(kubeResp);
        final int statusCode = kubeResp.code();
        final byte[] bodyBytes = kubeResp.body() == null ? new byte[0] : kubeResp.body();

        T body = applyBodyHandler(responseBodyHandler, statusCode, responseHeaders, bodyBytes);

        return new HttpResponse<T>() {
            @Override public int statusCode() { return statusCode; }
            @Override public HttpRequest request() { return request; }
            @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return responseHeaders; }
            @Override public T body() { return body; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return request.uri(); }
            @Override public Version version() { return Version.HTTP_1_1; }
        };
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

    private static <T> T applyBodyHandler(BodyHandler<T> handler, int statusCode,
                                          HttpHeaders responseHeaders, byte[] bodyBytes)
            throws IOException {
        BodySubscriber<T> subscriber = handler.apply(new ResponseInfo() {
            @Override public int statusCode() { return statusCode; }
            @Override public HttpHeaders headers() { return responseHeaders; }
            @Override public Version version() { return Version.HTTP_1_1; }
        });
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override public void request(long n) { }
            @Override public void cancel() { }
        });
        subscriber.onNext(List.of(ByteBuffer.wrap(bodyBytes)));
        subscriber.onComplete();
        try {
            return subscriber.getBody().toCompletableFuture().get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static byte[] drainBody(HttpRequest request) throws IOException {
        Optional<HttpRequest.BodyPublisher> bp = request.bodyPublisher();
        if (bp.isEmpty()) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompletableFuture<Void> done = new CompletableFuture<>();
        bp.get().subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;
            @Override public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                s.request(Long.MAX_VALUE);
            }
            @Override public void onNext(ByteBuffer buf) {
                byte[] chunk = new byte[buf.remaining()];
                buf.get(chunk);
                try {
                    out.write(chunk);
                } catch (IOException e) {
                    subscription.cancel();
                    done.completeExceptionally(e);
                }
            }
            @Override public void onError(Throwable t) { done.completeExceptionally(t); }
            @Override public void onComplete() { done.complete(null); }
        });
        try {
            done.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return out.toByteArray();
    }

    private static Map<String, String> flattenHeaders(HttpRequest request) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, List<String>> e : request.headers().map().entrySet()) {
            if (!e.getValue().isEmpty()) {
                result.put(e.getKey(), String.join(",", e.getValue()));
            }
        }
        return result;
    }

    private static HttpHeaders convertHeaders(io.fabric8.kubernetes.client.http.HttpResponse<?> resp) {
        Map<String, List<String>> map = new HashMap<>();
        for (String name : resp.headers().keySet()) {
            List<String> values = resp.headers(name);
            if (values != null && !values.isEmpty()) {
                map.put(name, new ArrayList<>(values));
            } else {
                String single = resp.header(name);
                if (single != null) {
                    map.put(name, List.of(single));
                }
            }
        }
        return HttpHeaders.of(map, (a, b) -> true);
    }

    // --- async / websocket: not used by JolokiaClient -------------------------

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, BodyHandler<T> handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, BodyHandler<T> handler,
                                                            HttpResponse.PushPromiseHandler<T> pushHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {
        throw new UnsupportedOperationException();
    }

    // --- metadata: sensible defaults; the kube transport owns the real config -

    @Override public Optional<CookieHandler> cookieHandler()      { return Optional.empty(); }
    @Override public Optional<Duration> connectTimeout()          { return Optional.empty(); }
    @Override public Redirect followRedirects()                   { return Redirect.NEVER; }
    @Override public Optional<ProxySelector> proxy()              { return Optional.empty(); }
    @Override public SSLContext sslContext() {
        try { return SSLContext.getDefault(); }
        catch (Exception e) { throw new UncheckedIOException(new IOException(e)); }
    }
    @Override public SSLParameters sslParameters()                { return new SSLParameters(); }
    @Override public Optional<Authenticator> authenticator()      { return Optional.empty(); }
    @Override public Version version()                            { return Version.HTTP_1_1; }
    @Override public Optional<Executor> executor()                { return Optional.empty(); }
}
