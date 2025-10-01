package org.jolokia.server.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * Utility class for helping in managing and finding provider parts of an objectname
 *
 * @author roland
 * @since 16.12.13
 */
public final class ProviderUtil {

    private ProviderUtil() { }

    // Split pattern for detecting the provider ($1: provider, $2: remaining part)
    public static final Pattern PROVIDER_PATTERN = Pattern.compile("^([^@:]*)@(.*)$");

    /**
     * Extract an provider part from an {@link ObjectName}.
     *
     * @param pName object name from which the provider should be extracted. Must not be null.
     * @return object name containing the extracted provider (which can be null) and the object name itself. Is never null.
     */
    public static ProviderObjectNamePair extractProvider(String pName) throws MalformedObjectNameException {
        if (pName == null) {
            pName = "*:*";
        }
        Matcher matcher = ProviderUtil.PROVIDER_PATTERN.matcher(pName);
        if (matcher.matches()) {
            return new ProviderObjectNamePair(matcher.group(1), matcher.group(2));
        } else {
            return new ProviderObjectNamePair(null, pName);
        }
    }

    /**
     * Extract an provider part from an {@link ObjectName}.
     *
     * @param pName object name from which the provider should be extracted. Must not be null.
     * @return object name containing the extracted provider (which can be null) and the object name itself. Is never null.
     */
    public static ProviderObjectNamePair extractProvider(ObjectName pName) throws MalformedObjectNameException {
        return extractProvider(pName.getCanonicalName());
    }

    /**
     * Check whether the given name matches the given provider. I.e. whether its domain
     * starts with <code>pProvider + "@"</code>. A special case is, when the object name
     * doesnt contain any provider (or not even a domain (and the provider given is <code>null</code>,
     * then this is considered a match as well.
     *
     * @param pProvider provider to check against
     * @param pName object name to check
     * @return true if the object name matches the provider
     */
    public static boolean matchesProvider(String pProvider, ObjectName pName) {
        String domain = pName.getDomain();
        Matcher matcher = PROVIDER_PATTERN.matcher(domain);
        if (matcher.matches()) {
            String providerFromName = matcher.group(1);
            return providerFromName.equals(pProvider);
        }
        // If no provider given in the name or no domain was given, check whether our provider is null
        return pProvider == null;
    }

    /**
     * Helper class holding the provider (can be null) and an object name belonging to this provider)
     */
    public static final class ProviderObjectNamePair {
        private final String provider;
        private final ObjectName objectName;

        private ProviderObjectNamePair(String pProvider, String pObjectName) throws MalformedObjectNameException {
            provider = pProvider;
            objectName = new ObjectName(pObjectName);
        }

        public String getProvider() {
            return provider;
        }

        public ObjectName getObjectName() {
            return objectName;
        }
    }

}
