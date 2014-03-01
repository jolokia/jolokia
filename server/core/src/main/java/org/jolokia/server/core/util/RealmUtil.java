package org.jolokia.server.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * Utility class for helping in managing and finding realm parts of an objectname
 *
 * @author roland
 * @since 16.12.13
 */
public final class RealmUtil {

    private RealmUtil() { }

    // Split pattern for detecting the realm ($1: realm, $2: remaining part)
    public static final Pattern REALM_PATTERN = Pattern.compile("^([^@:]*)@(.*)$");

    /**
     * Extract an realm part from an {@link ObjectName}.
     *
     * @param pName object name from which the realm should be extracted. Must not be null.
     * @return object name containing the extracted realm (which can be null) and the object name itself. Is never null.
     */
    public static RealmObjectNamePair extractRealm(String pName) throws MalformedObjectNameException {
        if (pName == null) {
            throw new IllegalArgumentException("Object name can not be null");
        }
        Matcher matcher = RealmUtil.REALM_PATTERN.matcher(pName);
        if (matcher.matches()) {
            return new RealmObjectNamePair(matcher.group(1),matcher.group(2));
        } else {
            return new RealmObjectNamePair(null,pName);
        }
    }

    /**
     * Extract an realm part from an {@link ObjectName}.
     *
     * @param pName object name from which the realm should be extracted. Must not be null.
     * @return object name containing the extracted realm (which can be null) and the object name itself. Is never null.
     */
    public static RealmObjectNamePair extractRealm(ObjectName pName) throws MalformedObjectNameException {
        return extractRealm(pName.toString());
    }

    /**
     * Check whether the given name matches the given realm. I.e. whether its domain
     * starts with <code>pRealm + "@"</code>. A special case is, when the object name
     * doesnt contain any realm (or not even a domain( and the realm given is <code>null</code>,
     * then this is considered a match as well.
     *
     * @param pRealm realm to check against
     * @param pName object name to check
     * @return true if the object name matches the realm
     */
    public static boolean matchesRealm(String pRealm,ObjectName pName) {
        String domain = pName.getDomain();
        Matcher matcher = REALM_PATTERN.matcher(domain);
        if (matcher.matches()) {
            String realmFromName = matcher.group(1);
            return realmFromName.equals(pRealm);
        }
        // If no realm given in the name or no domain was given, check whether our realm is null
        return pRealm == null;
    }

    /**
     * Helper class holding the realm (can be null) and an object name belonging to this realm)
     */
    public static final class RealmObjectNamePair {
        private String realm;
        private ObjectName objectName;

        private RealmObjectNamePair(String pRealm, String pObjectName) throws MalformedObjectNameException {
            realm = pRealm;
            objectName = new ObjectName(pObjectName);
        }

        public String getRealm() {
            return realm;
        }

        public ObjectName getObjectName() {
            return objectName;
        }
    }

}
