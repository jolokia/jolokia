package org.jolokia.server.core.util;

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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling escaping of strings and paths.
 *
 * @author roland
 * @since 15.03.11
 */
public final class EscapeUtil {

    /**
     * Escape character used for path escaping as it can be used
     * in a regexp
     */
    public static final String PATH_ESCAPE = "!";

    /**
     * Escape character for escaping CSV type string as it can be used in a
     * regexp. E.g. a backslash (\ or "\\") must be doubled (\\ or "\\\\")
     */
    public static final String CSV_ESCAPE = "\\\\";
    
    // Compile patterns in advance and cache them
    static final Map<String,Pattern[]> SPLIT_PATTERNS = new HashMap<String, Pattern[]>();
    static {
        for (String param[] : new String[][] {
                { PATH_ESCAPE, "/"} ,
                { CSV_ESCAPE, ","},
                { CSV_ESCAPE, "="}
        }) {
            String esc = param[0];
            String del = param[1];
            SPLIT_PATTERNS.put(esc + del, createSplitPatterns(esc, del));
        }
    }

    private EscapeUtil() {}

    /**
     * Combine a list of strings to a single path with proper escaping.
     *
     * @param pParts parts to combine
     * @return the combined path
     */
    public static String combineToPath(List<String> pParts) {
        if (pParts != null && pParts.size() > 0) {
            StringBuilder buf = new StringBuilder();
            Iterator<String> it = pParts.iterator();
            while (it.hasNext()) {
                String part = it.next();
                buf.append(escapePart(part != null ? part : "*"));
                if (it.hasNext()) {
                    buf.append("/");
                }
            }
            return buf.toString();
        } else {
            return null;
        }
    }

    /**
     * Parse a string path and return a list of split up parts.
     *
     * @param pPath the path to parse. Can be null
     * @return list of path elements or null if the initial path is null.
     */
    public static List<String> parsePath(String pPath) {
        // Special cases which simply implies 'no path'
        if (pPath == null || pPath.equals("") || pPath.equals("/")) {
            return null;
        }
        return replaceWildcardsWithNull(split(pPath, PATH_ESCAPE, "/"));
    }

    /**
     * Get the path as a reverse stack with the first element of the path on top
     *
     * @param pPath path to parse
     * @return stack of arguments in reverse order or an empty stack if path was null or empty
     */
    public static Stack<String> extractElementsFromPath(String pPath) {
        return reversePath(parsePath(pPath));
    }

    /**
     * Reverse path and return as a stack. First path element is on top
     * of the stack.
     *
     * @param pathParts path to reverse
     * @return reversed path or an empty stack if no path parts are given. Never return null.
     */
    public static Stack<String> reversePath(List<String> pathParts) {
        Stack<String> pathStack = new Stack<String>();
        if (pathParts != null) {
            // Needs first extra argument at top of the stack
            for (int i = pathParts.size() - 1;i >=0;i--) {
                pathStack.push(pathParts.get(i));
            }
        }
        return pathStack;
    }

    /**
     * Split a string on a delimiter, respecting escaping with an escape char. Assuming
     * that a backslash (<code>\</code>) is used as escape char, then the following
     * replacement rules apply:
     *
     * <ul>
     *  <li>
     *    <code>\</code><em>delimiter</em> for the delimiter as literal
     *  </li>
     *  <li>
     *    <code>\\</code> for backslashes
     *  </li>
     *  <li>
     *   <code>\</code><em>(everything else)</em> is the same as <em>(everything else)</em>.
     *  </li>
     *
     * @param pArg argument to split
     * @param pEscape escape pattern as it can be used in a regular expression.
     * @param pDelimiter delimiter to use
     * @return the split string as list or an empty array if the argument was null
     */
    public static List<String> split(String pArg,String pEscape, String pDelimiter) {
        if (pArg != null) {
            ArrayList<String> ret = new ArrayList<String>();
            Pattern[] pattern = SPLIT_PATTERNS.get(pEscape + pDelimiter);
            if (pattern == null) {
                pattern = createSplitPatterns(pEscape, pDelimiter);
                SPLIT_PATTERNS.put(pEscape + pDelimiter,pattern);
            }

            final Matcher m = pattern[0].matcher(pArg);
            while (m.find() && m.start(1) != pArg.length()) {
                // Finally unescape all escaped parts. Trailing escapes are captured before the delimiter applies
                String trailingEscapes = m.group(2);
                ret.add(pattern[1].matcher(m.group(1) + (trailingEscapes != null ? trailingEscapes : "")).replaceAll("$1"));
            }
            return ret;
        } else {
            return null;
        }
    }

    /**
     * Split but return an array which is never null (but might be empty)
     *
     * @param pArg argument to split
     * @param pEscape single character used for escaping
     * @param pDelimiter delimiter to use
     * @return the split string as list or an empty array if the argument was null
     */
    public static String[] splitAsArray(String pArg, String pEscape, String pDelimiter) {
        if (pArg != null) {
            List<String> elements = split(pArg, pEscape, pDelimiter);
            return elements.toArray(new String[elements.size()]);
        } else {
            return new String[0];
        }
    }

    // ===================================================================================

    // Create a split pattern for a given delimiter
    private static Pattern[] createSplitPatterns(String pEscape, String pDel) {
        return new Pattern[] {
                // Escape ($1: Everything before the delimiter, $2: Trailing escaped values (optional)
                Pattern.compile("(.*?)" + // Any chars
                                "(?:" +
                                   // The delimiter not preceded by an escape (but pairs of escape & value can be in
                                   // are allowed before nevertheless). I negative-look-before (?<!) is used for this
                                   // purpose
                                   "(?<!" + pEscape  + ")((?:" + pEscape + ".)*)" + pDel + "|" +
                                   "$" +    // or end-of-line
                                 ")",Pattern.DOTALL),

                // Unescape, group must match unescaped value
                Pattern.compile(pEscape + "(.)",Pattern.DOTALL)
        };
    }

    // Escape a single part
    private static final Pattern ESCAPE_PATTERN = Pattern.compile(PATH_ESCAPE);

    private static final Pattern SLASH_PATTERN = Pattern.compile("/");
    private static String escapePart(String pPart) {
        return SLASH_PATTERN.matcher(
                ESCAPE_PATTERN.matcher(pPart).replaceAll(PATH_ESCAPE + PATH_ESCAPE)).replaceAll(PATH_ESCAPE + "/");
    }


    /**
     * For GET requests, where operation arguments and values to write are given in
     * string representation as part of the URL, certain special tags are used to indicate
     * special values:
     *
     * <ul>
     *    <li><code>[null]</code> for indicating a null value</li>
     *    <li><code>""</code> for indicating an empty string</li>
     * </ul>
     *
     * This method converts these tags to the proper value. If not a tag, the original
     * value is returned.
     *
     * If you need this tag values in the original semantics, please use POST requests.
     *
     * @param pValue the string value to check for a tag
     * @return the converted value or the original one if no tag has been found.
     */
    public static String convertSpecialStringTags(String pValue) {
        if ("[null]".equals(pValue)) {
            // Null marker for get requests
            return null;
        } else if ("\"\"".equals(pValue)) {
            // Special string value for an empty String
            return "";
        } else {
            return pValue;
        }
    }

    private static List<String> replaceWildcardsWithNull(List<String> pParts) {
        if (pParts  == null) {
            return null;
        }
        List<String> ret = new ArrayList<String>(pParts.size());
        for (String part : pParts) {
            ret.add("*".equals(part) ? null : part);
        }
        return ret;
    }

    /**
     * Base64 encoding methods of Authentication
     * Taken from http://iharder.sourceforge.net/current/java/base64/ (public domain)
     * and adapted for our needs here.
     *
     * @param pEncoded base64 encoded string to decode
     * @return decoded bytes
     */
    public static byte[] decodeBase64(String pEncoded) {
        if( pEncoded == null ){
            throw new IllegalArgumentException("Input string was null.");
        }

        byte[] inBytes;
        try {
            inBytes = pEncoded.getBytes("US-ASCII");
        }
        catch( java.io.UnsupportedEncodingException uee ) {
            inBytes = pEncoded.getBytes();
        }

        if( inBytes.length == 0 ) {
            return new byte[0];
        } else if( inBytes.length < 4 ){
            throw new IllegalArgumentException(
            "Base64-encoded string must have at least four characters, but length specified was " + inBytes.length);
        }   // end if

        return decodeBytes(inBytes);
    }

    // ========================================================================================================

    // Do the conversion to bytes
    private static byte[] decodeBytes(byte[] pInBytes) {
        byte[] decodabet = DECODABET;

        int    len34   = pInBytes.length * 3 / 4;       // Estimate on array size
        byte[] outBuff = new byte[ len34 ]; // Upper limit on size of output
        int    outBuffPosn = 0;             // Keep track of where we're writing

        byte[] b4        = new byte[4];     // Four byte buffer from source, eliminating white space
        int    b4Posn    = 0;               // Keep track of four byte input buffer
        int    i         = 0;               // Source array counter
        byte   sbiCrop   = 0;               // Low seven bits (ASCII) of input
        byte   sbiDecode = 0;               // Special value from DECODABET

        for( i = 0; i < 0 + pInBytes.length; i++ ) {  // Loop through source

            sbiCrop = (byte)(pInBytes[i] & 0x7f); // Only the low seven bits
            sbiDecode = decodabet[ sbiCrop ];   // Special value

            // White space, Equals sign, or legit Base64 character
            // Note the values such as -5 and -9 in the
            // DECODABETs at the top of the file.
            if( sbiDecode >= WHITE_SPACE_ENC )  {
                if( sbiDecode >= EQUALS_SIGN_ENC ) {
                    b4[ b4Posn++ ] = sbiCrop;           // Save non-whitespace
                    if( b4Posn > 3 ) {                  // Time to decode?
                        outBuffPosn += decode4to3( b4, 0, outBuff, outBuffPosn);
                        b4Posn = 0;

                        // If that was the equals sign, break out of 'for' loop
                        if( sbiCrop == EQUALS_SIGN ) {
                            break;
                        }
                    }
                }
            }
            else {
                // There's a bad input character in the Base64 stream.
                throw new IllegalArgumentException(String.format(
                "Bad Base64 input character '%d' in array position %d", pInBytes[i], i ) );
            }
        }

        byte[] out = new byte[ outBuffPosn ];
        System.arraycopy( outBuff, 0, out, 0, outBuffPosn );
        return out;
    }

    private static int decode4to3(
            byte[] source, int srcOffset,
            byte[] destination, int destOffset) {

        verifyArguments(source, srcOffset, destination, destOffset);


        if( source[ srcOffset + 2] == EQUALS_SIGN ) {
            int outBuff =   ( ( DECODABET[ source[ srcOffset    ] ] & 0xFF ) << 18 )
                          | ( ( DECODABET[ source[ srcOffset + 1] ] & 0xFF ) << 12 );

            destination[ destOffset ] = (byte)( outBuff >>> 16 );
            return 1;
        }
        else if( source[ srcOffset + 3 ] == EQUALS_SIGN ) {
            //CHECKSTYLE:OFF
            int outBuff =   ( ( DECODABET[ source[ srcOffset     ] ] & 0xFF ) << 18 )
                          | ( ( DECODABET[ source[ srcOffset + 1 ] ] & 0xFF ) << 12 )
                          | ( ( DECODABET[ source[ srcOffset + 2 ] ] & 0xFF ) <<  6 );
            //CHECKSTYLE:ON

            destination[ destOffset     ] = (byte)( outBuff >>> 16 );
            destination[ destOffset + 1 ] = (byte)( outBuff >>>  8 );
            return 2;
        } else {
            //CHECKSTYLE:OFF
            int outBuff =   ( ( DECODABET[ source[ srcOffset     ] ] & 0xFF ) << 18 )
                          | ( ( DECODABET[ source[ srcOffset + 1 ] ] & 0xFF ) << 12 )
                          | ( ( DECODABET[ source[ srcOffset + 2 ] ] & 0xFF ) <<  6)
                          | ( ( DECODABET[ source[ srcOffset + 3 ] ] & 0xFF )      );
            //CHECKSTYLE:ON

            destination[ destOffset     ] = (byte)( outBuff >> 16 );
            destination[ destOffset + 1 ] = (byte)( outBuff >>  8 );
            destination[ destOffset + 2 ] = (byte)( outBuff       );

            return 3;
        }
    }

    // Check for argument validity
    private static void verifyArguments(byte[] source, int srcOffset, byte[] destination, int destOffset) {
        // Lots of error checking and exception throwing
        if( source == null ){
            throw new IllegalArgumentException( "Source array was null." );
        }   // end if
        if( destination == null ){
            throw new IllegalArgumentException( "Destination array was null." );
        }   // end if
        if( srcOffset < 0 || srcOffset + 3 >= source.length ){
            throw new IllegalArgumentException( String.format(
            "Source array with length %d cannot have offset of %d and still process four bytes.", source.length, srcOffset ) );
        }   // end if
        if( destOffset < 0 || destOffset +2 >= destination.length ){
            throw new IllegalArgumentException( String.format(
            "Destination array with length %d cannot have offset of %d and still store three bytes.", destination.length, destOffset ) );
        }   // end if
    }

    // =================================================================================================
    // Constants

    /**
     * Translates a Base64 value to either its 6-bit reconstruction value
     * or a negative number indicating some other meaning.
     **/
    private static final byte[] DECODABET = {
        -9,-9,-9,-9,-9,-9,-9,-9,-9,                 // Decimal  0 -  8
        -5,-5,                                      // Whitespace: Tab and Linefeed
        -9,-9,                                      // Decimal 11 - 12
        -5,                                         // Whitespace: Carriage Return
        -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 14 - 26
        -9,-9,-9,-9,-9,                             // Decimal 27 - 31
        -5,                                         // Whitespace: Space
        -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,              // Decimal 33 - 42
        62,                                         // Plus sign at decimal 43
        -9,-9,-9,                                   // Decimal 44 - 46
        63,                                         // Slash at decimal 47
        52,53,54,55,56,57,58,59,60,61,              // Numbers zero through nine
        -9,-9,-9,                                   // Decimal 58 - 60
        -1,                                         // Equals sign at decimal 61
        -9,-9,-9,                                      // Decimal 62 - 64
        0,1,2,3,4,5,6,7,8,9,10,11,12,13,            // Letters 'A' through 'N'
        14,15,16,17,18,19,20,21,22,23,24,25,        // Letters 'O' through 'Z'
        -9,-9,-9,-9,-9,-9,                          // Decimal 91 - 96
        26,27,28,29,30,31,32,33,34,35,36,37,38,     // Letters 'a' through 'm'
        39,40,41,42,43,44,45,46,47,48,49,50,51,     // Letters 'n' through 'z'
        -9,-9,-9,-9                                 // Decimal 123 - 126
    };

    private static final byte WHITE_SPACE_ENC = -5; // Indicates white space in encoding
    private static final byte EQUALS_SIGN_ENC = -1; // Indicates equals sign in encoding
    private static final byte EQUALS_SIGN = (byte)'=';
}
