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
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.*;

import com.sun.net.httpserver.HttpServer;
import org.jolokia.Version;
import org.jolokia.jvmagent.security.KeyStoreUtil;
import org.jolokia.test.util.EnvTestUtil;
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
                "executor=single"
        };

        for (String c : configs) {
            roundtrip(c,true);
        }
    }


    @Test(expectedExceptions = IOException.class,expectedExceptionsMessageRegExp = ".*401.*")
    public void httpWithAuthenticationRejected() throws Exception {
        Map config = new HashMap();
        config.put("user","roland");
        config.put("password","s!cr!t");
        config.put("port","0");
        roundtrip(config,true);
    }

    @Test
    public void serverPicksThePort() throws Exception {
        roundtrip("host=localhost,port=0",true);
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
     */

    @Test
    public void t_11_https_only() throws Exception {
        httpsRoundtrip("agentId=test",false);
    }

    @Test
    public void t_12_with_keystore() throws Exception {
        httpsRoundtrip("keystore=" + getResourcePath("/keystore") + ",keystorePassword=jetty7",false);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*without.*key.*")
    public void serverCertWithoutKey() throws Exception {
        httpsRoundtrip("serverCert=" + getCertPath("server/cert.pem"),false);
    }

    @Test
    public void t_131_pem_without_ca() throws Exception {
        httpsRoundtrip("serverCert=" + getCertPath("server/cert.pem") + "," +
                       "serverKey=" + getCertPath("server/key.pem"),
                       false);
    }

    @Test
    public void t_132_pem_with_ca() throws Exception {
        httpsRoundtrip(getFullCertSetup(), true);
    }

    @Test(expectedExceptions = IOException.class)
    public void t_21_self_signed_client_cert_fail() throws Exception {
        httpsRoundtrip("useSslClientAuthentication=true," + getFullCertSetup(),
                       true,
                       "client/self-signed-with-key-usage");
    }

    @Test
    public void t_22_signed_client_cert() throws Exception {
        // default is no extended client check
        httpsRoundtrip("useSslClientAuthentication=true," + getFullCertSetup(),
                       true,
                       "client/without-key-usage");
    }

    @Test
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

    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*403.*")
    public void t_2331_without_extended_client_key_usage() throws Exception {
        httpsRoundtrip("useSslClientAuthentication=true,extendedClientCheck=true," + getFullCertSetup(),
                       true,
                       "client/without-key-usage");
    }

    @Test
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

    @Test
    public void t_241_with_client_principal() throws Exception {
        httpsRoundtrip("useSslClientAuthentication=true,clientPrincipal=O\\=jolokia.org\\,CN\\=Client signed with client key usage,"
                       + getFullCertSetup(),
                       true,
                       "client/with-key-usage");
    }

    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*403.*")
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

    // ==================================================================================================

    private String getFullCertSetup() {
        return "serverCert=" + getCertPath("server/cert.pem") + "," +
               "serverKey=" + getCertPath("server/key.pem") + "," +
               "caCert=" + getCertPath("ca/cert.pem");
    }


    @Test
    public void sslWithAdditionalHttpsSettings() throws Exception {
        httpsRoundtrip("keystore=" + getResourcePath("/keystore") +
                       ",keystorePassword=jetty7" +
                       ",config=" + getResourcePath("/agent-test-additionalHttpsConf.properties"),
                       false);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*password.*")
    public void invalidConfig() throws IOException, InterruptedException {
        JvmAgentConfig cfg = new JvmAgentConfig("user=roland,port=" + EnvTestUtil.getFreePort());
        Thread.sleep(1000);
        new JolokiaServer(cfg,false);
    }

    @Test
    public void customHttpServer() throws IOException, NoSuchFieldException, IllegalAccessException {
        HttpServer httpServer = HttpServer.create();
        JvmAgentConfig cfg = new JvmAgentConfig("");
        JolokiaServer server = new JolokiaServer(httpServer,cfg,false);
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
        checkServer(new JvmAgentConfig(pConfig),pDoRequest);
    }

    private void roundtrip(String pConfig, boolean pDoRequest) throws Exception {
        JvmAgentConfig config = new JvmAgentConfig(prepareConfigString(pConfig));
        checkServer(config, pDoRequest);
    }

    private void httpsRoundtrip(String pConfig, boolean pValidateCa) throws Exception {
        httpsRoundtrip(pConfig,pValidateCa,"client/with-key-usage");
    }

    private void httpsRoundtrip(String pConfig, boolean pValidateCa, String clientCert) throws Exception {
        JvmAgentConfig config = new JvmAgentConfig(
                prepareConfigString("host=localhost,port=" + EnvTestUtil.getFreePort() + ",protocol=https," + pConfig));
        HostnameVerifier verifier = new HostnameVerifier() {
            @Override
            public boolean verify(String host, SSLSession sslSession) {
                return true;
            }
        };
        checkServer(config, true, verifier, pValidateCa, clientCert);
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
        JolokiaServer server = new JolokiaServer(pConfig,false);
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
                    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                    kmf.init(ks, "1234".toCharArray());
                    kms = kmf.getKeyManagers() ;
                }
                sc.init(kms, tms, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            }
            URL url = new URL(server.getUrl());
            String resp = EnvTestUtil.readToString(url.openStream());
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


}
