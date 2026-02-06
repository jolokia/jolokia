/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.client;

import org.jolokia.core.util.PropertyUtil;

/**
 * <p>Options for configuring {@link org.jolokia.client.JolokiaClient} in a declarative way. Normally
 * {@link org.jolokia.client.JolokiaClient} is built using {@link org.jolokia.client.JolokiaClientBuilder},
 * but it can also be used with {@link javax.management.remote.JMXConnector} implementation, so we need
 * a place to declare all supported options.</p>
 *
 * <p>This enum is a bit similar to the server-side {@code org.jolokia.server.core.config.ConfigKey}, but for
 * client configuration. See also {@code org.jolokia.jvmagent.JolokiaServerConfig#initHttpsRelatedSettings()} for the
 * server-side options used to configure the Jolokia JVM Agent.</p>
 */
public enum JolokiaClientOption {

    /** Location (path) to {@link java.security.KeyStore} with client certificate and private key */
    KEYSTORE("keystore"),

    /** Password to entire {@link java.security.KeyStore} with client certificate(s) and private key(s) */
    KEYSTORE_PASSWORD("keystorePassword"),

    /** Alias for a key entry in {@link #KEYSTORE} if there's more than one entry */
    KEYSTORE_ALIAS("keystoreAlias"),

    /** Location (path) to a {@link java.security.KeyStore} with server/ca data to validate the server */
    TRUSTSTORE("truststore"),

    /** Password to entire {@link java.security.KeyStore} with server/ca certificate(s) */
    TRUSTSTORE_PASSWORD("truststorePassword"),

    /** Location of a X.509 client certificate (PEM or DER) when not using {@link #KEYSTORE} */
    CLIENT_CERTIFICATE("clientCertificate"),

    /** Location of PKCS#1 (RSA) or PKCS#8 private key (PEM or DER) matching the {@link #CLIENT_CERTIFICATE} */
    CLIENT_KEY("clientKey"),

    /** Algorithm to be used for {@link java.security.KeyFactory#getInstance(String)} if using {@link #CLIENT_KEY} */
    CLIENT_KEY_ALGORITHM("clientKeyAlgorithm"),

    /** Password for the {@link #CLIENT_KEY} or a key inside the {@link #KEYSTORE} for a given {@link #KEYSTORE_ALIAS} */
    CLIENT_KEY_PASSWORD("clientKeyPassword"),

    /** Location of a CA certificate (PEM or DER) when not using {@link #TRUSTSTORE} */
    CA_CERTIFICATE("caCertificate"),

    /** Username for Basic authentication */
    USERNAME("username"),

    /** Password for Basic authentication */
    PASSWORD("password"),

    /** Connection (establishment) timeout in milliseconds */
    CONNECTION_TIMEOUT("connectionTimeout"),

    /** Read (socket) timeout in milliseconds */
    READ_TIMEOUT("readTimeout"),

    /** Whether to get {@link javax.management.openmbean.OpenType} information with {@code list} operation */
    OPEN_TYPES("openTypes");

    private final String option;

    JolokiaClientOption(String option) {
        this.option = option;
    }

    public String option() {
        return option;
    }

    /**
     * Get the name of the option as a system property for {@link System#getProperty(String)}.
     *
     * @return key, prefixed with {@code jolokia.}
     */
    public String asSystemProperty() {
        return PropertyUtil.asJolokiaSystemProperty(option);
    }

    /**
     * Get the name of the option as an environment variable for {@link System#getenv(String)}.
     *
     * @return key, prefixed with {@code JOLOKIA_} in env-variable convention (e.g., {@code JOLOKIA_CA_CERTIFICATE}.
     */
    public String asEnvVariable() {
        return PropertyUtil.asJolokiaEnvVariable(option);
    }

}
