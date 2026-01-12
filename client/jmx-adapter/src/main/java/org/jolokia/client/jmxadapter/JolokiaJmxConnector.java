/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.client.jmxadapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;

import org.jolokia.client.JolokiaClientBuilder;
import org.jolokia.client.JolokiaClientOption;
import org.jolokia.core.util.CryptoUtil;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Map;

/**
 * <p>Jolokia implementation of {@link JMXConnector} that uses a {@link org.jolokia.client.JolokiaClient} to connect
 * to the remote Jolokia Agent. Not all operations on returned {@link JMXConnector#getMBeanServerConnection()}} are
 * supported.</p>
 *
 * <p>The {@link org.jolokia.client.JolokiaClient} will be configured using options passed to {@link #connect(Map)},
 * but these may be overridden by system properties and environment variables (which are of highest precedence).</p>
 */
public class JolokiaJmxConnector implements JMXConnector {

    protected final JMXServiceURL serviceUrl;
    // environment passed during initialization (can be overridden during connect(env))
    private final Map<String, ?> initEnvironment;

    // RemoteJmxAdapter holds the Jolokia Client instance and performs operations on remote Jolokia Agent
    protected RemoteJmxAdapter adapter;

    private final NotificationBroadcasterSupport broadcasterSupport = new NotificationBroadcasterSupport();
    private long clientNotifSeqNo = 1L;
    private String connectionId;

    /**
     * Create new Jolokia {@link JMXConnector} for given {@link JMXServiceURL} and environment.
     * @param serviceURL
     * @param env
     * @throws IOException
     */
    public JolokiaJmxConnector(JMXServiceURL serviceURL, Map<String, ?> env) throws IOException {
        this.serviceUrl = serviceURL;
        this.initEnvironment = env;
    }

    @Override
    public void connect() throws IOException {
        connect(Collections.emptyMap());
    }

    @Override
    public void connect(Map<String, ?> env) throws IOException {
        String protocol = this.serviceUrl.getProtocol();
        if (!protocol.startsWith("jolokia")) {
            throw new MalformedURLException(String.format("Invalid URL %s : Only protocol \"jolokia[+http[s]]\" is supported (not %s)", this.serviceUrl, protocol));
        }

        Map<String, Object> mergedEnv = mergedEnvironment(env);

        final String internalProtocol = getJolokiaProtocol();
        final JolokiaClientBuilder clientBuilder = new JolokiaClientBuilder().url(
            String.format("%s://%s:%d%s",
                internalProtocol,
                addBracketsIfIPv6(this.serviceUrl.getHost()),
                this.serviceUrl.getPort(),
                this.serviceUrl.getURLPath()
            ));

        // sun.tools.jconsole.ProxyClient.tryConnect passes jmx.remote.x.check.stub=true, but we no longer
        // use it. We can't make JConsole to pass more configuration options, so we'll collect them
        // from system properties / env variables as a fallback
        // see: https://github.com/jolokia/jolokia/issues/911

        Map<String, Object> copy = new HashMap<>(env);

        // dedicated support for "jmx.remote.credentials" parameter
        // com.sun.jmx.remote.security.JMXPluggableAuthenticator supports String[] type of credentials
        // with username and password elements
        if (copy.containsKey(CREDENTIALS)) {
            Object credentials = mergedEnv.get(CREDENTIALS);
            if (!(credentials instanceof String[])) {
                throw new IllegalArgumentException("\"jmx.remote.credentials\" should be a two-element String array");
            }
            copy.put(JolokiaClientOption.USERNAME.asSystemProperty(), ((String[]) credentials)[0]);
            copy.put(JolokiaClientOption.PASSWORD.asSystemProperty(), ((String[]) credentials)[1]);
        }

        clientBuilder.user(stringProperty(copy, JolokiaClientOption.USERNAME));
        clientBuilder.password(stringProperty(copy, JolokiaClientOption.PASSWORD));

        clientBuilder.connectionTimeout(intProperty(copy, JolokiaClientOption.CONNECTION_TIMEOUT));
        clientBuilder.socketTimeout(intProperty(copy, JolokiaClientOption.READ_TIMEOUT));

        // these methods require Keystore location (JKS or PKCS12), but we may load the material from
        // individual keys and certificates too. We'll always recreate the key/truststore:
        // - in case there's a client key alias
        // - in case user specified individual locations for certs / keys
        clientBuilder.truststore(buildTruststore(copy));
        clientBuilder.keystore(buildKeystore(copy));
        // the passwords may be cleared if the keystore is rebuilt by buildTruststore()/buildKeystore()
        clientBuilder.truststorePassword(stringProperty(copy, JolokiaClientOption.TRUSTSTORE_PASSWORD));
        clientBuilder.keystorePassword(stringProperty(copy, JolokiaClientOption.KEYSTORE_PASSWORD));

        // this can be used both for individual key file and a key entry in the keystore
        clientBuilder.keyPassword(stringProperty(copy, JolokiaClientOption.CLIENT_KEY_PASSWORD));

        this.adapter = new RemoteJmxAdapter(clientBuilder.build());

        postCreateAdapter();
    }

    /**
     * Return single environment map where the passed environment overrides the one specified when the connector
     * was created
     *
     * @param env
     * @return
     */
    protected Map<String, Object> mergedEnvironment(Map<String, ?> env) {
        Map<String, Object> mergedEnv = new HashMap<>();
        if (this.initEnvironment != null) {
            mergedEnv.putAll(this.initEnvironment);
        }
        if (env != null) {
            mergedEnv.putAll(env);
        }
        return mergedEnv;
    }

    /**
     * The the actual protocol ({@code http} or {@code https}) from the {@link JMXServiceURL}. For compatibility
     * reasons, the protocol is {@code https} if the port ends with {@code 443}. But the recommended approach
     * is to use <a href="https://www.ietf.org/rfc/rfc2609.html#section-2.1">RFC 2609</a> syntax, where {@code url-scheme}
     * may contain {@code +} sign. So we can explicitly use {@code service:jmx:jolokia+http:} or
     * {@code service:jmx:jolokia+https:}.
     * @return
     */
    public String getJolokiaProtocol() {
        String protocol = "http";
        String jmxProtocol = this.serviceUrl.getProtocol();
        if (jmxProtocol.contains("+")) {
            String after = jmxProtocol.substring(jmxProtocol.indexOf('+') + 1);
            if (after.equals("http") || after.equals("https")) {
                protocol = after;
            } else {
                throw new IllegalArgumentException("Unknown Jolokia JMX protocol: " + jmxProtocol);
            }
        } else {
            // legacy + heuristics
            if (String.valueOf(this.serviceUrl.getPort()).endsWith("443")) {
                protocol = "https";
            }
        }
        return protocol;
    }

    /**
     * Wraps IP6 address within {@code []}, because {@link JMXServiceURL} constructor trims this syntax.
     * @param host
     * @return
     */
    private String addBracketsIfIPv6(String host) {
        if (host.contains(":")) {
            return "[" + host + "]";
        } else {
            return host;
        }
    }

    protected void postCreateAdapter() {
        this.connectionId = this.adapter.getId();
        this.broadcasterSupport.sendNotification(new JMXConnectionNotification(JMXConnectionNotification.OPENED, this, this.connectionId, this.clientNotifSeqNo++, "Successful connection", null));
    }

    @Override
    public MBeanServerConnection getMBeanServerConnection() {
        return this.adapter;
    }

    @Override
    public MBeanServerConnection getMBeanServerConnection(Subject delegationSubject) {
        throw new UnsupportedOperationException("Jolokia uses credentials specified at connect() time. Create new Jolokia connector for different user (subject) instead.");
    }

    @Override
    public void close() {
        this.broadcasterSupport.sendNotification(new JMXConnectionNotification(JMXConnectionNotification.CLOSED, this, this.connectionId, clientNotifSeqNo++, "Client has been closed", null));
        this.adapter = null;
    }

    @Override
    public void addConnectionNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
        this.broadcasterSupport.addNotificationListener(listener, filter, handback);
    }

    @Override
    public void removeConnectionNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
        this.broadcasterSupport.removeNotificationListener(listener);
    }

    @Override
    public void removeConnectionNotificationListener(NotificationListener l, NotificationFilter f, Object handback) throws ListenerNotFoundException {
        this.broadcasterSupport.removeNotificationListener(l, f, handback);
    }

    @Override
    public String getConnectionId() {
        return this.connectionId;
    }

    static String stringProperty(Map<String, Object> config, JolokiaClientOption option) {
        Object v = config.get(option.asSystemProperty());
        String value;
        if (v instanceof String) {
            value = (String) v;
        } else {
            throw new IllegalArgumentException(option.asSystemProperty() + " should be a String value");
        }

        String sys = System.getProperty(option.asSystemProperty());
        if (sys != null && !sys.trim().isEmpty()) {
            value = sys;
        }

        String env = System.getenv(option.asEnvVariable());
        if (env != null && !env.trim().isEmpty()) {
            value = env;
        }

        return value;
    }

    static int intProperty(Map<String, Object> config, JolokiaClientOption option) {
        Object v = config.get(option.asSystemProperty());
        int value;
        if (v instanceof String) {
            value = Integer.parseInt((String) v);
        } else if (v instanceof Number n) {
            value = n.intValue();
        } else {
            throw new IllegalArgumentException(option.asSystemProperty() + " should be a String or numeric value");
        }

        String sys = System.getProperty(option.asSystemProperty());
        if (sys != null && !sys.trim().isEmpty()) {
            value = Integer.parseInt(sys);
        }

        String env = System.getenv(option.asEnvVariable());
        if (env != null && !env.trim().isEmpty()) {
            value = Integer.parseInt(env);
        }

        return value;
    }

    /**
     * Returns a {@link KeyStore} to be used as server validation truststore. If the store is created
     * from individual certificate(s), the truststore returned has no password.
     * @param config
     * @return
     */
    static KeyStore buildTruststore(Map<String, Object> config) {
        String existingTruststore = stringProperty(config, JolokiaClientOption.TRUSTSTORE);
        String existingTruststorePassword = stringProperty(config, JolokiaClientOption.TRUSTSTORE_PASSWORD);

        String caCertificates = stringProperty(config, JolokiaClientOption.CA_CERTIFICATE);
        if (existingTruststore != null && caCertificates != null) {
            throw new IllegalArgumentException("Both \"" + JolokiaClientOption.TRUSTSTORE.asSystemProperty() + "\" and \""
                + JolokiaClientOption.CA_CERTIFICATE.asSystemProperty() + "\" specified. Use only one.");
        }

        if (existingTruststore != null) {
            // trying to get the full truststore
            try {
                KeyStore truststore = KeyStore.getInstance("PKCS12");
                try (FileInputStream fis = new FileInputStream(Path.of(existingTruststore).toFile())) {
                    truststore.load(fis, existingTruststorePassword == null ? new char[0] : existingTruststorePassword.toCharArray());
                }
                boolean hasCertificate = false;
                for (Enumeration<String> e = truststore.aliases(); e.hasMoreElements(); ) {
                    String alias = e.nextElement();
                    if (truststore.isCertificateEntry(alias)) {
                        hasCertificate = true;
                    }
                }
                if (!hasCertificate) {
                    throw new IllegalArgumentException("Truststore " + existingTruststore + " does not contain certificate entries");
                }
                return truststore;
            } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
                throw new IllegalArgumentException("Problem loading truststore from " + existingTruststore, e);
            }
        } else if (caCertificates != null) {
            // trying to assemble the truststore from the certificate(s) found in single PEM/DER encoded
            // X.509 (for DER - only one) stream
            try {
                CertificateFactory cf = CertificateFactory.getInstance("X509");
                try (FileInputStream fis = new FileInputStream(Path.of(caCertificates).toFile())) {
                    Collection<? extends Certificate> certificates = cf.generateCertificates(fis);

                    // PKCS12 should be "dual format" PKCS12KeyStore$DualFormatPKCS12 that also supports JKS
                    KeyStore truststore = KeyStore.getInstance("PKCS12");
                    int idx = 0;
                    for (Certificate cert : certificates) {
                        truststore.setCertificateEntry(String.format("cert-%02d", idx++), cert);
                    }
                    // remove, because we've created new Keystore
                    config.remove(JolokiaClientOption.TRUSTSTORE_PASSWORD.asSystemProperty());
                    return truststore;
                }
            } catch (CertificateException | IOException | KeyStoreException e) {
                throw new IllegalArgumentException("Problem loading CA/server certificate from " + caCertificates, e);
            }
        }

        // no truststore or certificate. default truststore will be used by Jolokia Client
        return null;
    }

    /**
     * Returns a {@link KeyStore} to be used as a keystore for client TLS. If the store is created
     * from individual certificate/key, the keystore returned has no password.
     * @param config
     * @return
     */
    static KeyStore buildKeystore(Map<String, Object> config) {
        String existingKeystore = stringProperty(config, JolokiaClientOption.KEYSTORE);
        String existingKeystorePassword = stringProperty(config, JolokiaClientOption.KEYSTORE_PASSWORD);
        String existingKeystoreKeyAlias = stringProperty(config, JolokiaClientOption.KEYSTORE_ALIAS);

        String clientCertificate = stringProperty(config, JolokiaClientOption.CLIENT_CERTIFICATE);
        String clientKey = stringProperty(config, JolokiaClientOption.CLIENT_KEY);
        String clientKeyAlgorithm = stringProperty(config, JolokiaClientOption.CLIENT_KEY_ALGORITHM);

        if (existingKeystore != null && (clientCertificate != null || clientKey != null)) {
            throw new IllegalArgumentException("Both \"" + JolokiaClientOption.KEYSTORE.asSystemProperty() + "\" and \""
                + JolokiaClientOption.CLIENT_CERTIFICATE.asSystemProperty() + "\"/\""
                + JolokiaClientOption.CLIENT_KEY.asSystemProperty() + "\" specified. Use only one.");
        }

        // password for a key - whether it's from the keystore or separate file
        String keyPassword = stringProperty(config, JolokiaClientOption.CLIENT_KEY_PASSWORD);

        if (existingKeystore != null) {
            // trying to get the full keystore
            try {
                KeyStore keystore = KeyStore.getInstance("PKCS12");
                try (FileInputStream fis = new FileInputStream(Path.of(existingKeystore).toFile())) {
                    keystore.load(fis, existingKeystorePassword == null ? new char[0] : existingKeystorePassword.toCharArray());
                }
                int keyCount = 0;
                boolean hasMatchingKey = false;
                for (Enumeration<String> e = keystore.aliases(); e.hasMoreElements(); ) {
                    String alias = e.nextElement();
                    if (keystore.isKeyEntry(alias)) {
                        keyCount++;
                        if (alias.equals(existingKeystoreKeyAlias)) {
                            hasMatchingKey = true;
                        }
                    }
                }
                if (keyCount == 0) {
                    throw new IllegalArgumentException("Keystore " + existingKeystore + " does not contain key entries");
                }
                if (existingKeystoreKeyAlias != null && !hasMatchingKey) {
                    throw new IllegalArgumentException("Keystore " + existingKeystore + " does not contain key with \"" + existingKeystoreKeyAlias + "\" alias");
                }
                if (keyCount == 1) {
                    // we can simply return this keystore
                    return keystore;
                } else {
                    // we have to rebuild the keystore with just one key entry of the matching alias
                    KeyStore newKeystore = KeyStore.getInstance("PKCS12");
                    char[] pwd = keyPassword == null ? new char[0] : keyPassword.toCharArray();
                    Key key = keystore.getKey(existingKeystoreKeyAlias, pwd);
                    newKeystore.setKeyEntry(existingKeystoreKeyAlias, key, pwd, keystore.getCertificateChain(existingKeystoreKeyAlias));

                    // remove, because we've created new Keystore
                    config.remove(JolokiaClientOption.KEYSTORE_PASSWORD.asSystemProperty());

                    return newKeystore;
                }
            } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException |
                     UnrecoverableKeyException e) {
                throw new IllegalArgumentException("Problem loading truststore from " + existingKeystore, e);
            }
        } else if (clientCertificate != null && clientKey != null) {
            // trying to assemble the keystore from a single certificate and private key (PEM or DER)
            try {
                CertificateFactory cf = CertificateFactory.getInstance("X509");
                Certificate certificate;
                try (FileInputStream fis = new FileInputStream(Path.of(clientCertificate).toFile())) {
                    Collection<? extends Certificate> certificates = cf.generateCertificates(fis);
                    if (certificates.isEmpty()) {
                        throw new IllegalArgumentException(clientCertificate + " does not contain any X.509 certificate");
                    } else if (certificates.size() > 1) {
                        throw new IllegalArgumentException(clientCertificate + " contains more than one X.509 certificate. Expected only one certificate matching a private key from " + clientKey);
                    }
                    certificate = certificates.iterator().next();
                }

                CryptoUtil.CryptoStructure cryptoData = CryptoUtil.decodePemIfNeeded(Path.of(clientKey).toFile());
                byte[] keyBytes = cryptoData.derData();

                KeySpec keySpec = CryptoUtil.decodePrivateKey(cryptoData, keyPassword.toCharArray());

                PrivateKey privateKey = CryptoUtil.generatePrivateKey(keySpec, clientKeyAlgorithm);

                if (!CryptoUtil.keysMatch(privateKey, certificate.getPublicKey())) {
                    throw new IllegalArgumentException("Private key from " + clientKey + " and public key from " + clientCertificate + " do not match");
                }

                KeyStore keystore = KeyStore.getInstance("PKCS12");
                keystore.setKeyEntry("key", privateKey, keyPassword.toCharArray(), new Certificate[] { certificate });

                // remove, because we've created new Keystore
                config.remove(JolokiaClientOption.KEYSTORE_PASSWORD.asSystemProperty());

                return keystore;
            } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException |
                     InvalidKeySpecException e) {
                throw new IllegalArgumentException("Problem loading client certificate and key from "
                    + clientCertificate + ", " + clientKey, e);
            }
        }

        return null;
    }

}
