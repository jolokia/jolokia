package org.jolokia.util;/*
 *
 * Copyright 2014 Roland Huss
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
 * Base64 routine taken from http://iharder.sourceforge.net/current/java/base64/ (public domain)
 * It has be tailored to suite our needs, so some things likes compression and
 * multiline output has been removed for the sake of simplicity.
 *
 * @author roland
 * @since 13/09/15
 */
public class Base64Util {

    /**
     * Base64 encoding methods of Authentication
     * Taken from http://iharder.sourceforge.net/current/java/base64/ (public domain)
     * and adapted for our needs here.
     */
    public static byte[] decode(String s) {

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

    /**
     * Encodes a byte array into Base64 notation.
     * Does not GZip-compress data.
     *
     * @param source The data to convert
     * @return The data in Base64-encoded form
     * @throws NullPointerException if source array is null
     * @since 1.4
     */
    public static String encode(byte[] source) {
        byte[] encoded = encodeBytesToBytes( source, source.length);

        try {
            return new String(encoded, "US-ASCII");
        }
        catch (java.io.UnsupportedEncodingException uue) {
            return new String( encoded );
        }
    }

    // ==========================================================================================================
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


    public static byte[] encodeBytesToBytes(byte[] source, int len) {
        int encLen = (len / 3) * 4 + (len % 3 > 0 ? 4 : 0); // Bytes needed for actual encoding
        byte[] outBuff = new byte[encLen];

        int d = 0;
        int e = 0;
        int len2 = len - 2;
        for (; d < len2; d+=3, e+=4) {
            encode3to4( source, d, 3, outBuff, e);
        }

        if(d < len) {
            encode3to4( source, d, len - d, outBuff, e);
            e += 4;
        }

        // Only resize array if we didn't guess it right.
        if (e <= outBuff.length - 1){
            byte[] finalOut = new byte[e];
            System.arraycopy(outBuff,0, finalOut,0,e);
            return finalOut;
        } else {
            return outBuff;
        }
    }


    private static byte[] encode3to4(
            byte[] source, int srcOffset, int numSigBytes,
            byte[] destination, int destOffset) {

        //           1         2         3
        // 01234567890123456789012345678901 Bit position
        // --------000000001111111122222222 Array position from threeBytes
        // --------|    ||    ||    ||    | Six bit groups to index ALPHABET
        //          >>18  >>12  >> 6  >> 0  Right shift necessary
        //                0x3f  0x3f  0x3f  Additional AND

        // Create buffer with zero-padding if there are only one or two
        // significant bytes passed in the array.
        // We have to shift left 24 in order to flush out the 1's that appear
        // when Java treats a value as negative that is cast from a byte to an int.
        int inBuff =   ( numSigBytes > 0 ? ((source[ srcOffset     ] << 24) >>>  8) : 0 )
                     | ( numSigBytes > 1 ? ((source[ srcOffset + 1 ] << 24) >>> 16) : 0 )
                     | ( numSigBytes > 2 ? ((source[ srcOffset + 2 ] << 24) >>> 24) : 0 );

        switch( numSigBytes )
        {
            case 3:
                destination[ destOffset     ] = ALPHABET[ (inBuff >>> 18)        ];
                destination[ destOffset + 1 ] = ALPHABET[ (inBuff >>> 12) & 0x3f ];
                destination[ destOffset + 2 ] = ALPHABET[ (inBuff >>>  6) & 0x3f ];
                destination[ destOffset + 3 ] = ALPHABET[ (inBuff       ) & 0x3f ];
                return destination;

            case 2:
                destination[ destOffset     ] = ALPHABET[ (inBuff >>> 18)        ];
                destination[ destOffset + 1 ] = ALPHABET[ (inBuff >>> 12) & 0x3f ];
                destination[ destOffset + 2 ] = ALPHABET[ (inBuff >>>  6) & 0x3f ];
                destination[ destOffset + 3 ] = EQUALS_SIGN;
                return destination;

            case 1:
                destination[ destOffset     ] = ALPHABET[ (inBuff >>> 18)        ];
                destination[ destOffset + 1 ] = ALPHABET[ (inBuff >>> 12) & 0x3f ];
                destination[ destOffset + 2 ] = EQUALS_SIGN;
                destination[ destOffset + 3 ] = EQUALS_SIGN;
                return destination;

            default:
                return destination;
        }   // end switch
    }   // end encode3to4


    // ===============================================================================================
    // Constants

    /** The 64 valid Base64 values. */
    /* Host platform me be something funny like EBCDIC, so we hardcode these values. */
    private final static byte[] ALPHABET = {
        (byte)'A', (byte)'B', (byte)'C', (byte)'D', (byte)'E', (byte)'F', (byte)'G',
        (byte)'H', (byte)'I', (byte)'J', (byte)'K', (byte)'L', (byte)'M', (byte)'N',
        (byte)'O', (byte)'P', (byte)'Q', (byte)'R', (byte)'S', (byte)'T', (byte)'U',
        (byte)'V', (byte)'W', (byte)'X', (byte)'Y', (byte)'Z',
        (byte)'a', (byte)'b', (byte)'c', (byte)'d', (byte)'e', (byte)'f', (byte)'g',
        (byte)'h', (byte)'i', (byte)'j', (byte)'k', (byte)'l', (byte)'m', (byte)'n',
        (byte)'o', (byte)'p', (byte)'q', (byte)'r', (byte)'s', (byte)'t', (byte)'u',
        (byte)'v', (byte)'w', (byte)'x', (byte)'y', (byte)'z',
        (byte)'0', (byte)'1', (byte)'2', (byte)'3', (byte)'4', (byte)'5',
        (byte)'6', (byte)'7', (byte)'8', (byte)'9', (byte)'+', (byte)'/'
    };

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
