package org.jolokia.jvmagent;

/*
 * Copyright 2009-2014 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.*;

import com.sun.net.httpserver.HttpServer;
import org.jolokia.Version;
import org.jolokia.jvmagent.security.KeyStoreUtil;
import org.jolokia.test.util.EnvTestUtil;
import org.jolokia.util.Base64Util;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @author nevenr
 * @since 31.08.11
 */
public class JolokiaServerTest {

    @Test
    public void http() throws Exception {
        String configs[] = {
                null,
                "executor=fixed,threadNr=5",
                "executor=cached",
                "executor=single",
                "executor=fixed,threadNr=5,threadNamePrefix=JolokiaServerTestExecutorFixed",
                "executor=cached,threadNamePrefix=JolokiaServerTestExecutorFixedCached",
                "executor=single,threadNamePrefix=JolokiaServerTestExecutorFixedSingle",
                "executor=fixed,threadNamePrefix=jolokia-,threadNr=5",
        };

        for (String c : configs) {
            roundtrip(c, true);
        }
    }


    @Test(expectedExceptions = IOException.class,expectedExceptionsMessageRegExp = ".*401.*")
    public void httpWithAuthenticationRejected() throws Exception {
        Map config = new HashMap();
        config.put("user", "roland");
        config.put("password", "s!cr!t");
        config.put("port", "0");
        roundtrip(config, true);
    }

    @Test
    public void serverPicksThePort() throws Exception {
        roundtrip("host=localhost,port=0", true);
    }


    // SSL Checks ========================================================================================

    /*

    Test Scenarios
    ==============
    - 1 no client auth:
      - 11 https only (no certs)
      - 12 with keystore
      - 13 with PEM server cert
        - 131 without CA validation
        - 132 with CA validation (positive)
    - 2 with client auth:
      - 21 self-signed client cert --> fail
      - 22 properly signed client cert --> ok
      - 23 with 'extended key usage check'
        - 231 with extended key usage == client --> ok
        - 232 with extended key usage == server --> fail
        - 233 with no extended key usage:
          - 2331 with 'extendedClientCheck' options == true --> fail
          - 2332 with 'extendedClientCheck' option == false --> ok
      - 24 with 'clientPrincipal' given
        - 241 matching clientPrincipal --> ok
        - 241 non-matching clientPrincipal --> fail
      - 25 no CA given to verify against --> fail
      - 26 with clientPrincipal and basic auth
     */

    @Test(enabled=false)
    public void t_11_https_only() throws Exception {
        httpsRoundtrip("agentId=test", false);
    }

    @Test(enabled=false)
    public void t_12_with_keystore() throws Exception {
        httpsRoundtrip("keystore=" + getResourcePath("/keystore") + ",keystorePassword=jetty7", false);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*without.*key.*")
    public void serverCertWithoutKey() throws Exception {
        httpsRoundtrip("serverCert=" + getCertPath("server/cert.pem"), false);
    }

    @Test(enabled=false)
    public void t_131_pem_without_ca() throws Exception {
        httpsRoundtrip("serverCert=" + getCertPath("server/cert.pem") + "," +
                       "serverKey=" + getCertPath("server/key.pem"),
                       false);
    }

    @Test(enabled=false)
    public void t_132_pem_with_ca() throws Exception {
        httpsRoundtrip(getFullCertSetup(), true);
    }


    @Test(expectedExceptions = IOException.class)
    public void t_21_self_signed_client_cert_fail() throws Exception {
        httpsRoundtrip("useSslClientAuthentication=true," + getFullCertSetup(),
                       true,
                       "client/self-signed-with-key-usage");
    }

    @Test(enabled=false)
    public void t_22_signed_client_cert() throws Exception {
        // default is no extended client check
        httpsRoundtrip("useSslClientAuthentication=true," + getFullCertSetup(),
                       true,
                       "client/without-key-usage");
    }

    @Test(enabled=false)
    public void t_231_with_extended_client_key_usage() throws Exception {
        httpsRoundtrip("useSslClientAuthentication=true,extendedClientCheck=true," + getFullCertSetup(),
                       true,
                       "client/with-key-usage");
    }

    @Test(expectedExceptions = IOException.class)
    public void t_232_with_wrong_extended_client_key_usage() throws Exception {
        httpsRoundtrip("useSslClientAuthentication=true,extendedClientCheck=true," + getFullCertSetup(),
                       true,
                       "client/with-wrong-key-usage");
    }

    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*403.*", enabled=false)
    public void t_2331_without_extended_client_key_usage() throws Exception {
        httpsRoundtrip("useSslClientAuthentication=true,extendedClientCheck=true," + getFullCertSetup(),
                       true,
                       "client/without-key-usage");
    }

    @Test(enabled=false)
    public void t_2332_without_extended_client_key_usage_allowed() throws Exception {
        httpsRoundtrip("useSslClientAuthentication=true,extendedClientCheck=false," + getFullCertSetup(),
                       true,
                       "client/with-key-usage");
    }

    @Test(expectedExceptions = IOException.class)
    public void t_2333_with_wrong_extended_client_key_usage_allowed() throws Exception {
        httpsRoundtrip("useSslClientAuthentication=true,extendedClientCheck=false," + getFullCertSetup(),
                       true,
                       "client/with-wrong-key-usage");
    }

    @Test(enabled=false)
    public void t_241_with_client_principal() throws Exception {
        httpsRoundtrip("useSslClientAuthentication=true,clientPrincipal=O\\=jolokia.org\\,CN\\=Client signed with client key usage,"
                       + getFullCertSetup(),
                       true,
                       "client/with-key-usage");
    }

    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*403.*", enabled=false)
    public void t_242_with_wrong_client_principal() throws Exception {
        httpsRoundtrip("useSslClientAuthentication=true,clientPrincipal=O=microsoft.com,"
                       + getFullCertSetup(),
                       true,
                       "client/with-key-usage");
    }


    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*no CA.*")
    public void t_25_no_ca_given() throws Exception {
        httpsRoundtrip("useSslClientAuthentication=true,"
                       + "serverCert=" + getCertPath("server/cert.pem") + "," +
                       "serverKey=" + getCertPath("server/key.pem"),
                       true,
                       "client/with-key-usage");
    }

    @Test(enabled=false)
    public void t_261_with_client_principal() throws Exception {
        httpsRoundtrip("authMode=basic,user=admin,password=password,useSslClientAuthentication=true,clientPrincipal=O\\=jolokia.org\\,CN\\=Client signed with client key usage,"
                       + getFullCertSetup(),
                       true,
                       "client/with-key-usage");
    }

    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*401.*", enabled=false)
    public void t_262_with_wrong_client_principal() throws Exception {
        httpsRoundtrip("authMode=basic,user=admin,password=password,useSslClientAuthentication=true,clientPrincipal=O=microsoft.com,"
                       + getFullCertSetup(),
                       true,
                       "client/with-key-usage");
    }

    @Test(enabled=false)
    public void t_263_with_basic_auth() throws Exception {
        httpsRoundtrip("authMode=basic,user=admin,password=password,useSslClientAuthentication=true,clientPrincipal=O=microsoft.com,"
                       + getFullCertSetup(),
                       true,
                       "client/with-key-usage",
                       "admin:password");
    }

    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*401.*", enabled=false)
    public void t_264_with_wrong_basic_auth() throws Exception {
        httpsRoundtrip("authMode=basic,user=admin,password=password,useSslClientAuthentication=true,clientPrincipal=O=microsoft.com,"
                       + getFullCertSetup(),
                       true,
                       "client/with-key-usage",
                       "admin:wrong");
    }

    @Test(expectedExceptions = IOException.class)
    public void t_264_with_basic_auth_and_wrong_client_cert() throws Exception {
        httpsRoundtrip("authMode=basic,user=admin,password=password,useSslClientAuthentication=true,clientPrincipal=O=microsoft.com,"
                       + getFullCertSetup(),
                       true,
                       "client/self-signed-with-key-usage",
                       "admin:wrong");
    }

    // ==================================================================================================

    private String getFullCertSetup() {
        return "serverCert=" + getCertPath("server/cert.pem") + "," +
               "serverKey=" + getCertPath("server/key.pem") + "," +
               "caCert=" + getCertPath("ca/cert.pem");
    }

    private String getFullCertSha256Setup() {
        return "serverCert=" + getCertPath("server/cert2.pem") + "," +
               "serverKey=" + getCertPath("server/key2.pem") + "," +
               "caCert=" + getCertPath("ca/cert.pem");
    }


    @Test(enabled=false)
    public void sslWithAdditionalHttpsSettings() throws Exception {
        httpsRoundtrip("keystore=" + getResourcePath("/keystore") +
                       ",keystorePassword=jetty7" +
                       ",config=" + getResourcePath("/agent-test-additionalHttpsConf.properties"),
                       false);
    }

    @Test(groups = "java7")
    public void sslWithSpecialHttpsSettings() throws Exception {
        String certSetup = getFullCertSetup();
        String disabledCertAlgorithms = Security.getProperty("jdk.certpath.disabledAlgorithms");
        if (disabledCertAlgorithms != null) {
            Set<String> set = new HashSet<String>(Arrays.asList(disabledCertAlgorithms.toUpperCase().split("\\s*,\\s*")));
            if (set.contains("SHA1")) {
                certSetup = getFullCertSha256Setup();
            }
        }
        JvmAgentConfig config = new JvmAgentConfig(
            prepareConfigString("host=localhost,port=" + EnvTestUtil.getFreePort() + ",protocol=https," +
                    certSetup + ",config=" +  getResourcePath("/agent-test-specialHttpsSettings.properties")));
        JolokiaServer server = new JolokiaServer(config, false);
        server.start();

        // Skipping hostname verification because the cert doesn't have a SAN of localhost
        HostnameVerifier verifier = createHostnameVerifier();

        HostnameVerifier oldVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        SSLSocketFactory oldSslSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();

        List<String> cipherSuites = Arrays.asList(config.getSSLCipherSuites());
        List<String> protocols = Arrays.asList(config.getSSLProtocols());

        final String[] protocolCandidates;
        if ("IBM Corporation".equals(System.getProperty("java.vendor"))) {
            /* IBM's VM is technically capable to use SSL but due to POODLE it has been disabled by default for quite a while and throws
             an exception if an attempt is made to use it. Take note that this can lead to a bit of confusion as the cipher suites all
             are prefixed with SSL_ on J9 (compared to TLS_ on OpenJDK/Oracle). */
            protocolCandidates = new String[]{"TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"};
        } else {
            protocolCandidates = new String[]{"TLSv1.2"};
            // Readd 1.3 when everywhere available:
            // protocolCandidates = new String[]{"TLSv1.2", "TLSv1.3"};
        }
        for (String protocol : protocolCandidates) {
            // Make sure at least one connection for this protocol succeeds (if expected to)
            boolean connectionSucceeded = false;

            for (String cipherSuite : oldSslSocketFactory.getSupportedCipherSuites()) {
                if (!cipherSuites.contains(cipherSuite))
                    continue;

                try {
                    TrustManager tms[] = getTrustManagers(true);
                    SSLContext sc = SSLContext.getInstance(protocol);
                    sc.init(new KeyManager[0], tms, new java.security.SecureRandom());

                    HttpsURLConnection.setDefaultHostnameVerifier(verifier);
                    HttpsURLConnection.setDefaultSSLSocketFactory(
                        new FakeSSLSocketFactory(sc.getSocketFactory(), new String[]{protocol}, new String[]{cipherSuite}));

                    URL url = new URL(server.getUrl());
                    String resp = EnvTestUtil.readToString(url.openStream());
                    assertTrue(
                        resp.matches(".*type.*version.*" + Version.getAgentVersion() + ".*"));
                    if (!protocols.contains(protocol) || !cipherSuites.contains(cipherSuite)) {
                        fail(String.format("Expected SSLHandshakeException with the %s protocol and %s cipher suite", protocol, cipherSuite));
                    }
                    connectionSucceeded = true;
                } catch (javax.net.ssl.SSLHandshakeException e) {
                    // We make sure at least one connection with this protocol succeeds if expected
                    // down below
                } finally {
                    HttpsURLConnection.setDefaultHostnameVerifier(oldVerifier);
                    HttpsURLConnection.setDefaultSSLSocketFactory(oldSslSocketFactory);
                }
            }

            if (protocols.contains(protocol) && !connectionSucceeded) {
                fail("Expected at least one connection to succeed on " + protocol);
            }
        }

        server.stop();
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*password.*")
    public void invalidConfig() throws IOException, InterruptedException {
        JvmAgentConfig cfg = new JvmAgentConfig("user=roland,port=" + EnvTestUtil.getFreePort());
        Thread.sleep(1000);
        new JolokiaServer(cfg, false);
    }

    @Test
    public void customHttpServer() throws IOException, NoSuchFieldException, IllegalAccessException {
        HttpServer httpServer = HttpServer.create();
        JvmAgentConfig cfg = new JvmAgentConfig("");
        JolokiaServer server = new JolokiaServer(httpServer, cfg, false);
        Field field = JolokiaServer.class.getDeclaredField("httpServer");
        field.setAccessible(true);
        assertNull(field.get(server));
        server.start();
        server.stop();
    }

    // ==================================================================

    private String getCertPath(String pCert) {
        return getResourcePath("/certs/" + pCert);
    }

    private String getResourcePath(String relativeResourcePath) {
        URL ksURL = this.getClass().getResource(relativeResourcePath);
        if (ksURL != null && "file".equalsIgnoreCase(ksURL.getProtocol())) {
            return URLDecoder.decode(ksURL.getPath());
        }
        throw new IllegalStateException(ksURL + " is not a file URL");
    }

    private void roundtrip(Map<String,String> pConfig, boolean pDoRequest) throws Exception {
        checkServer(new JvmAgentConfig(pConfig), pDoRequest);
    }

    private void roundtrip(String pConfig, boolean pDoRequest) throws Exception {
        JvmAgentConfig config = new JvmAgentConfig(prepareConfigString(pConfig));
        checkServer(config, pDoRequest);
    }

    private void httpsRoundtrip(String pConfig, boolean pValidateCa) throws Exception {
        httpsRoundtrip(pConfig, pValidateCa, "client/with-key-usage");
    }

    private void httpsRoundtrip(String pConfig, boolean pValidateCa, String clientCert) throws Exception {
        JvmAgentConfig config = new JvmAgentConfig(
                prepareConfigString("host=localhost,port=" + EnvTestUtil.getFreePort() + ",protocol=https," + pConfig));
        checkServer(config, true, createHostnameVerifier(), pValidateCa, clientCert);
    }

    private void httpsRoundtrip(String pConfig, boolean pValidateCa, String clientCert, String pUserPassword) throws Exception {
        JvmAgentConfig config = new JvmAgentConfig(
                prepareConfigString("host=localhost,port=" + EnvTestUtil.getFreePort() + ",protocol=https," + pConfig));
        checkServer(config, true, createHostnameVerifier(), pValidateCa, clientCert, pUserPassword);
    }

    private HostnameVerifier createHostnameVerifier() {
        return new HostnameVerifier() {
            @Override
            public boolean verify(String host, SSLSession sslSession) {
                return true;
            }
        };
    }

    private String prepareConfigString(String pConfig) throws IOException {
        String c = pConfig != null ? pConfig + "," : "";
        boolean portSpecified = c.contains("port=");
        c = c + "host=localhost,";
        if (!portSpecified) {
            int port = EnvTestUtil.getFreePort();
            c = c + "port=" + port;
        }
        return c;
    }

    private void checkServer(JvmAgentConfig pConfig, boolean pDoRequest) throws Exception {
        checkServer(pConfig, pDoRequest, null, false, null);
    }

    private TrustManager[] getTrustManagers(final boolean pValidateCa)
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        if (!pValidateCa) {
            return new TrustManager[] { getAllowAllTrustManager() };
        } else {
            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(null);
            KeyStoreUtil.updateWithCaPem(keystore, new File(getCertPath("ca/cert.pem")));
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keystore);
            return tmf.getTrustManagers();
        }
    }

    private TrustManager getAllowAllTrustManager() {
        return new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
                System.out.println(certs);
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
                System.out.println(certs);
            }
        };
    }
    private void checkServer(JvmAgentConfig pConfig, boolean pDoRequest,
                             HostnameVerifier pVerifier,
                             boolean pValidateCa,
                             String pClientCert) throws Exception {
        checkServer(pConfig, pDoRequest, pVerifier, pValidateCa, pClientCert, null);
    }

    private void checkServer(JvmAgentConfig pConfig, boolean pDoRequest,
                             HostnameVerifier pVerifier,
                             boolean pValidateCa,
                             String pClientCert, String pUserPassword) throws Exception {
        JolokiaServer server = new JolokiaServer(pConfig, false);
        server.start();
        //Thread.sleep(2000);
        HostnameVerifier oldVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        SSLSocketFactory oldSslSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
        try {
            if (pDoRequest) {
                if (pVerifier != null) {
                    HttpsURLConnection.setDefaultHostnameVerifier(pVerifier);
                }
                TrustManager tms[] = null;
                    KeyManager kms[] = null;
                SSLContext sc = SSLContext.getInstance("SSL");
                tms = getTrustManagers(pValidateCa);
                if (pClientCert != null) {
                    KeyStore ks = KeyStore.getInstance("PKCS12");
                    InputStream fis = getClass().getResourceAsStream("/certs/" + pClientCert + "/cert.p12");
                    ks.load(fis, "1234".toCharArray());
                    KeyManagerFactory kmf;
                    if ("IBM Corporation".equals(System.getProperty("java.vendor"))) {
                        kmf = KeyManagerFactory.getInstance("IBMX509");
                    } else {
                        kmf = KeyManagerFactory.getInstance("SunX509");
                    }
                    kmf.init(ks, "1234".toCharArray());
                    kms = kmf.getKeyManagers() ;
                }
                sc.init(kms, tms, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            }
            URL url = new URL(server.getUrl());
            URLConnection uc = url.openConnection();
            if (pUserPassword != null) {
                uc.setRequestProperty("Authorization", "Basic " + Base64Util.encode(pUserPassword.getBytes()));
            }
            uc.connect();
            String resp = EnvTestUtil.readToString(uc.getInputStream());
            assertTrue(resp.matches(".*type.*version.*" + Version.getAgentVersion() + ".*"));
        } finally {
            server.stop();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {

            }
            HttpsURLConnection.setDefaultHostnameVerifier(oldVerifier);
            HttpsURLConnection.setDefaultSSLSocketFactory(oldSslSocketFactory);
        }
    }

    // FakeSSLSocketFactory wraps a normal SSLSocketFactory so it can set the explicit SSL / TLS
    // protocol version(s) and cipher suite(s)
    private static class FakeSSLSocketFactory extends SSLSocketFactory {
        private String[] cipherSuites;
        private String[] protocols;
        private SSLSocketFactory socketFactory;

        public FakeSSLSocketFactory(SSLSocketFactory socketFactory, String[] protocols, String[] cipherSuites) {
            super();
            this.socketFactory = socketFactory;
            this.protocols = protocols;
            this.cipherSuites = cipherSuites;
        }

        public Socket createSocket(InetAddress host, int port) throws IOException {
            return wrapSocket((SSLSocket)socketFactory.createSocket(host, port));
        }

        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return wrapSocket((SSLSocket)socketFactory.createSocket(s, host, port, autoClose));
        }

        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return wrapSocket((SSLSocket)socketFactory.createSocket(address, port, localAddress, localPort));
        }

        public Socket createSocket(String host, int port) throws IOException {
            return wrapSocket((SSLSocket)socketFactory.createSocket(host, port));
        }

        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return wrapSocket((SSLSocket)socketFactory.createSocket(host, port, localHost, localPort));
        }

        public String[] getDefaultCipherSuites() {
            return socketFactory.getDefaultCipherSuites();
        }

        public String[] getSupportedCipherSuites() { return socketFactory.getSupportedCipherSuites(); }

        private Socket wrapSocket(SSLSocket sslSocket) {
            sslSocket.setEnabledProtocols(this.protocols);
            sslSocket.setEnabledCipherSuites(this.cipherSuites);
            return sslSocket;
        }
    }
}
