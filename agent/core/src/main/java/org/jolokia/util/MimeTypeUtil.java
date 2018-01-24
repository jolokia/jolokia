package org.jolokia.util;

import java.util.regex.Pattern;

/**
 * Helper class for handling proper response mime types
 *
 * @author roland
 * @since 24.01.18
 */
public class MimeTypeUtil {


    /**
     * Extract the response mime type. This value is calculated for different situations:
     * <p>
     * <ul>
     * <li>If a callback is given and its valid, the mime type is "text/javascript"</li>
     * <li>Otherwise:
     * <ul>
     * <li>If a valid mimeType is given in the request ("text/plain", "application/json"), then this
     * mimet type is returned</li>
     * <li>If another mimeType is given, then "text/plain" is used</li>
     * <li>If no mimeType is given then a given default mime type is used, but also sanitized
     * as described above</li>
     * </ul>
     * </li>
     * </ul>
     *
     * @param pRequestMimeType the mimetype given in the request
     * @param defaultMimeType  the default mime type to use if none is given in the request
     * @param pCallback        a callback given (can be null)
     */
    public static String getResponseMimeType(String pRequestMimeType, String defaultMimeType, String pCallback) {

        // For a valid given callback, return "text/javascript" for proper inclusion
        if (pCallback != null && isValidCallback(pCallback)) {
            return "text/javascript";
        }

        // Pick up mime time from request, but sanitize
        if (pRequestMimeType != null) {
            return sanitize(pRequestMimeType);
        }

        // Use the given default mime type (possibly picked up from a configuration)
        return sanitize(defaultMimeType);
    }

    private static String sanitize(String mimeType) {
        for (String accepted : new String[]{
            "application/json",
            "text/plain"
        }) {
            if (accepted.equalsIgnoreCase(mimeType)) {
                return accepted;
            }
        }
        return "text/plain";
    }

    /**
     * Check that a callback matches a javascript function name. The argument must be not null
     *
     * @param pCallback callback to verify
     * @return true if valud, false otherwise
     */
    public static boolean isValidCallback(String pCallback) {
        Pattern validJavaScriptFunctionNamePattern =
            Pattern.compile("^[$A-Z_][0-9A-Z_$]*$", Pattern.CASE_INSENSITIVE);
        return validJavaScriptFunctionNamePattern.matcher(pCallback).matches();
    }

}