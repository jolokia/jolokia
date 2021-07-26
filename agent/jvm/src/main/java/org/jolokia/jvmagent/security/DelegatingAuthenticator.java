package org.jolokia.jvmagent.security;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Stack;

import javax.net.ssl.*;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.*;
import org.jolokia.util.AuthorizationHeaderParser;
import org.jolokia.util.EscapeUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Authenticator using JAAS for logging in with user and password for the given realm.
 *
 * @author roland
 * @since 26.05.14
 */
public class DelegatingAuthenticator extends Authenticator {

    private final URL delegateURL;
    private final PrincipalExtractor principalExtractor;
    private final String realm;
    private SSLSocketFactory sslSocketFactory;
    private HostnameVerifier hostnameVerifier;

    public DelegatingAuthenticator(String pRealm, String pUrl, String pPrincipalSpec, boolean pDisableCertCheck) {
        this.realm = pRealm;
        try {
            this.delegateURL = new URL(pUrl);
            this.principalExtractor = createPrincipalExtractor(pPrincipalSpec);
            if (pDisableCertCheck) {
                disableSSLCertificateChecking();
            }
        } catch (MalformedURLException exp) {
            throw new IllegalArgumentException("Invalid delegation url '" + pUrl + "' given: " + exp,exp);
        }
    }

    @Override
    public Result authenticate(HttpExchange pHttpExchange) {
        try {
            URLConnection connection = delegateURL.openConnection();
            String authorization = pHttpExchange.getRequestHeaders()
                .getFirst("Authorization");
            if(authorization == null){//In case middleware strips Authorization, allow alternate header
                authorization = pHttpExchange.getRequestHeaders()
                    .getFirst(AuthorizationHeaderParser.JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER);
            }
            connection.addRequestProperty("Authorization",
                authorization);
            connection.setConnectTimeout(2000);
            connection.connect();
            if (connection instanceof HttpURLConnection) {
                HttpURLConnection httpConnection = (HttpURLConnection) connection;
                if (connection instanceof HttpsURLConnection) {
                    HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
                    if (sslSocketFactory != null) {
                        httpsConnection.setSSLSocketFactory(sslSocketFactory);
                    }
                    if (hostnameVerifier != null) {
                        httpsConnection.setHostnameVerifier(hostnameVerifier);
                    }
                }
                return httpConnection.getResponseCode() == 200 ?
                        new Success(principalExtractor.extract(connection)) :
                        new Failure(401);
            } else {
                return new Failure(401);
            }
        } catch (final IOException e) {
            return prepareFailure(pHttpExchange, "Cannot call delegate url " + delegateURL + ": " + e, 503);
        } catch (final IllegalArgumentException e) {
            return prepareFailure(pHttpExchange, "Illegal Argument: " + e, 400);
        } catch (ParseException e) {
            return prepareFailure(pHttpExchange, "Invalid JSON response: " + e, 422);
        }
    }

    private Result prepareFailure(HttpExchange pHttpExchange, String pErrorDetails, int pCode) {
        pHttpExchange.getResponseHeaders().add("X-Error-Details", pErrorDetails);
        return new Failure(pCode);
    }

    private PrincipalExtractor createPrincipalExtractor(String pPrincipalExtractorSpec) {
        if (pPrincipalExtractorSpec == null || pPrincipalExtractorSpec.startsWith("empty:")) {
            return new EmptyPrincipalExtractor();
        } else if (pPrincipalExtractorSpec.startsWith("json:")) {
            return new JsonPathExtractor(pPrincipalExtractorSpec.substring("json:".length()));
        } else {
            throw new IllegalArgumentException("No principal extractor found for spec '" + pPrincipalExtractorSpec + "'");
        }
    }

    // =======================================================================================

    private interface PrincipalExtractor {
        HttpPrincipal extract(URLConnection connection) throws IOException, ParseException;
    }

    // Extract principal from a JSON object
    private class JsonPathExtractor implements PrincipalExtractor {

        private String path;

        public JsonPathExtractor(String pPath) {
            path = pPath;
        }

        @Override
        public HttpPrincipal extract(URLConnection connection) throws IOException, ParseException {
            final InputStreamReader isr = new InputStreamReader(connection.getInputStream());
            try {
                Object payload = new JSONParser().parse(isr);
                Stack<String> pathElements = EscapeUtil.extractElementsFromPath(path);
                Object result = payload;
                while (!pathElements.isEmpty()) {
                    if (result == null) {
                        throw new IllegalArgumentException("No path '" + path + "' found in " + payload.toString());
                    }
                    String key = pathElements.pop();
                    result = extractValue(result, key);
                }
                return new HttpPrincipal(result.toString(), realm);
            } finally {
                isr.close();
            }
        }

        private Object extractValue(Object payload, String key) {
            if (payload instanceof JSONObject) {
                return ((JSONObject) payload).get(key);
            } else if (payload instanceof JSONArray) {
                return ((JSONArray) payload).get(Integer.parseInt(key));
            } else {
                return null;
            }
        }
    }

    private class EmptyPrincipalExtractor implements PrincipalExtractor {
        public HttpPrincipal extract(URLConnection connection) {
            return new HttpPrincipal("",realm);
        }
    }

    // ============================================================================================

      private void disableSSLCertificateChecking() {
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                // Not implemented
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                // Not implemented
            }
        } };

        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());

            sslSocketFactory = sc.getSocketFactory();

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            hostnameVerifier = allHostsValid;
        } catch (KeyManagementException e) {
            throw new IllegalArgumentException("Disabling SSL certificate failed: " + e,e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Disabling SSL certificate failed: " + e,e);
        }
    }
}
