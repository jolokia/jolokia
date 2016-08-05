package org.jolokia.restrictor;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.util.*;

/*
 * Copyright 2009-2013 Roland Huss
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


/**
 * Factory for obtaining the proper {@link Restrictor}
 *
 * @author roland
 * @since Jul 28, 2009
 */
public final class RestrictorFactory {

    private static final Pattern URL_PATTERN = Pattern.compile("(http[s]?://)((.+):(.+)@)?(.+)");

    /* default */ static class URLConnectionResult {

        public final URLConnection urlConnection;

        public final String url;

        // URLConnection isn't queryable for "security headers";
        // make this available for unit testing
        public final String basicAuthHeaderValue;

        private URLConnectionResult(URLConnection urlConnection, String url,
                String basicAuthHeaderValue) {
            this.urlConnection = Objects.requireNonNull(urlConnection);
            this.url = Objects.requireNonNull(url);
            this.basicAuthHeaderValue = basicAuthHeaderValue;
        }

    }

    private RestrictorFactory() { }

    public static Restrictor createRestrictor(Configuration pConfig, LogHandler logHandler) {

        Restrictor customRestrictor = createCustomRestrictor(pConfig);
        if (customRestrictor != null) {
            logHandler.info("Using restrictor " + customRestrictor.getClass().getCanonicalName());
            return customRestrictor;
        }

        String rawLocation = pConfig.get(ConfigKey.POLICY_LOCATION);
        String location = NetworkUtil.replaceExpression(rawLocation);
        try {
            Restrictor ret = RestrictorFactory.lookupPolicyRestrictor(location);
            if (ret != null) {
                logHandler.info("Using policy access restrictor " + rawLocation);
                return ret;
            } else {
                logHandler.info("No access restrictor found, access to any MBean is allowed");
                return new AllowAllRestrictor();
            }
        } catch (IOException e) {
            logHandler.error("Error while accessing access restrictor at " + rawLocation +
                             ". Denying all access to MBeans for security reasons. Exception: " + e, e);
            return new DenyAllRestrictor();
        }
    }

    private static Restrictor createCustomRestrictor(Configuration pConfig) {
        String restrictorClassName = pConfig.get(ConfigKey.RESTRICTOR_CLASS);
        if (restrictorClassName == null) {
            return null;
        }
        Class restrictorClass = ClassUtil.classForName(restrictorClassName);
        if (restrictorClass == null) {
            throw new IllegalArgumentException("No custom restrictor class " + restrictorClassName + " found");
        }
        return lookupRestrictor(pConfig, restrictorClass);
    }

    private static Restrictor lookupRestrictor(Configuration pConfig, Class restrictorClass) {
        try {
            try {
                // Prefer constructor that takes configuration
                Constructor ctr = restrictorClass.getConstructor(Configuration.class);
                return (Restrictor) ctr.newInstance(pConfig);
            } catch (NoSuchMethodException exp) {
                // Fallback to default constructor
                Constructor defaultConstructor = restrictorClass.getConstructor();
                return (Restrictor) defaultConstructor.newInstance();
            }
        } catch (NoSuchMethodException exp) {
            throw new IllegalArgumentException("Cannot create custom restrictor for class " + restrictorClass + " " +
                                               "because neither a constructor with 'Configuration' as only element " +
                                               "nor a default constructor is available");
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot create an instance of custom restrictor class " + restrictorClass, e);
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("Cannot create an instance of custom restrictor class " + restrictorClass, e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Cannot create an instance of custom restrictor class " + restrictorClass, e);
        }
    }

    /**
     * Lookup a restrictor based on an URL
     *
     * @param pLocation classpath or URL representing the location of the policy restrictor
     *
     * @return the restrictor created or <code>null</code> if none could be found.
     * @throws IOException if reading of the policy stream failed
     */
    public static PolicyRestrictor lookupPolicyRestrictor(String pLocation) throws IOException {
        InputStream is;
        if (pLocation.startsWith("classpath:")) {
            String path = pLocation.substring("classpath:".length());
            is = ClassUtil.getResourceAsStream(path);
            if (is == null) {
                is = RestrictorFactory.class.getResourceAsStream(path);
            }
        } else {
            URLConnectionResult urlConnectionResult = buildUrlConnection(pLocation);
            is = urlConnectionResult.urlConnection.getInputStream();
        }
        return is != null ? new PolicyRestrictor(is) : null;
    }

    /* default */ static URLConnectionResult buildUrlConnection(String pLocation)
            throws MalformedURLException, IOException {
        Objects.requireNonNull(pLocation);

        Matcher matcher = URL_PATTERN.matcher(pLocation);

        String url = pLocation;
        String basicAuthHeaderValue = null;

        // We got a basic-auth user and password
        if (matcher.matches() && matcher.group(3) != null && matcher.group(4) != null) {
            url = matcher.group(1) + matcher.group(5);
            String auth = matcher.group(3) + ":" + matcher.group(4);
            basicAuthHeaderValue = "Basic " + Base64Util.encode(auth.getBytes(
                    "ISO-8859-1"));
        }

        URLConnection urlConnection = new URL(url).openConnection();
        if (basicAuthHeaderValue != null) {
            urlConnection.addRequestProperty("Authorization", basicAuthHeaderValue);
        }

        return new URLConnectionResult(urlConnection, url, basicAuthHeaderValue);
    }

}
