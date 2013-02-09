package org.jolokia.osgi;

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

import org.osgi.service.http.HttpContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 *Authentication context which uses a simple user/password credential pair
 *
* @author roland
* @since Jan 7, 2010
*/
class JolokiaAuthenticatedHttpContext extends JolokiaHttpContext {
    private final String user;
    private final String password;

    /**
     * Constructor
     *
     * @param pUser user to check against
     * @param pPassword password to check against
     */
    JolokiaAuthenticatedHttpContext(String pUser, String pPassword) {
        user = pUser;
        password = pPassword;
    }

    /** {@inheritDoc} */
    @Override
    public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String auth = request.getHeader("Authorization");
        if (auth == null || !verifyAuthentication(auth, user, password)) {
            response.setHeader("WWW-Authenticate","Basic realm=\"jolokia\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        } else {
            request.setAttribute(HttpContext.AUTHENTICATION_TYPE,"Basic");
            request.setAttribute(HttpContext.REMOTE_USER, user);
            return true;
        }
    }

    private boolean verifyAuthentication(String pAuth,String pUser, String pPassword) {
        StringTokenizer stok = new StringTokenizer(pAuth);
        String method = stok.nextToken();
        if (!"basic".equalsIgnoreCase(method)) {
            throw new IllegalArgumentException("Only BasicAuthentication is supported");
        }
        String b64Auth = stok.nextToken();
        String auth = new String(decode(b64Auth));

        int p = auth.indexOf(':');
        if (p != -1) {
            String name = auth.substring(0, p);
            String pwd = auth.substring(p+1);

            return name.trim().equals(pUser) &&
                    pwd.trim().equals(pPassword);
        } else {
            return false;
        }
    }


    // ========================================================================================================
    /**
     * Base64 encoding methods of Authentication
     * Taken from http://iharder.sourceforge.net/current/java/base64/ (public domain)
     * and adapted for our needs here.
     */
    byte[] decode(String s) {

        if( s == null ){
            throw new IllegalArgumentException("Input string was null.");
        }

        byte[] inBytes;
        try {
            inBytes = s.getBytes("US-ASCII");
        }
        catch( java.io.UnsupportedEncodingException uee ) {
            inBytes = s.getBytes();
        }

        if( inBytes.length == 0 ) {
            return new byte[0];
        } else if( inBytes.length < 4 ){
            throw new IllegalArgumentException(
            "Base64-encoded string must have at least four characters, but length specified was " + inBytes.length);
        }   // end if

        return decodeBytes(inBytes);
    }

    // Do the conversion to bytes
    private byte[] decodeBytes(byte[] pInBytes) {
        byte[] decodabet = JolokiaAuthenticatedHttpContext.DECODABET;

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
