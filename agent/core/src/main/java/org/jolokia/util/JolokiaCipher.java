package org.jolokia.util;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Random;

/**
 * Copied from https://github.com/sonatype/plexus-cipher/blob/plexus-cipher-1.7/src/main/java/org/sonatype/plexus/components/cipher/PBECipher.java
 *
 * @author nevenr
 * @since  12/09/2015
 */
public class JolokiaCipher {
    protected static final String STRING_ENCODING = "UTF8";

    protected static final int SPICE_SIZE = 16;

    protected static final int SALT_SIZE = 8;

    protected static final int CHUNK_SIZE = 16;

    protected static final String DIGEST_ALG = "SHA-256";

    protected static final String KEY_ALG = "AES";

    protected static final String CIPHER_ALG = "AES/CBC/PKCS5Padding";

    protected MessageDigest _digester;

    protected SecureRandom _secureRandom;

    protected boolean _onLinux = false;



    public JolokiaCipher() throws JolokiaCipherException {
        try {
            _digester = MessageDigest.getInstance(DIGEST_ALG);

            if (System.getProperty("os.name", "blah").toLowerCase().contains("linux")) {
                _onLinux = true;
            }

            if (_onLinux) {
                System.setProperty("securerandom.source", "file:/dev/./urandom");
            } else {
                _secureRandom = new SecureRandom();
            }

        } catch (NoSuchAlgorithmException e) {
            throw new JolokiaCipherException(e);
        }
    }

    private byte[] getSalt(final int sz)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        byte[] res;

        if (_secureRandom != null) {
            _secureRandom.setSeed(System.currentTimeMillis());
            res = _secureRandom.generateSeed(sz);
        } else {
            res = new byte[sz];
            Random r = new Random(System.currentTimeMillis());
            r.nextBytes(res);
        }

        return res;
    }

    public String encrypt64(final String clearText, final String password)
            throws JolokiaCipherException {
        try {
            byte[] clearBytes = clearText.getBytes(STRING_ENCODING);

            byte[] salt = getSalt(SALT_SIZE);

            // spin it :)
            if (_secureRandom != null) {
                new SecureRandom().nextBytes(salt);
            }

            Cipher cipher = createCipher(password.getBytes(STRING_ENCODING), salt, Cipher.ENCRYPT_MODE);

            byte[] encryptedBytes = cipher.doFinal(clearBytes);

            int len = encryptedBytes.length;

            byte padLen = (byte) (CHUNK_SIZE - (SALT_SIZE + len + 1) % CHUNK_SIZE);

            int totalLen = SALT_SIZE + len + padLen + 1;

            byte[] allEncryptedBytes = getSalt(totalLen);

            System.arraycopy(salt, 0, allEncryptedBytes, 0, SALT_SIZE);

            allEncryptedBytes[SALT_SIZE] = padLen;

            System.arraycopy(encryptedBytes, 0, allEncryptedBytes, SALT_SIZE + 1, len);

            return Base64Util.encode(allEncryptedBytes);
        } catch (Exception e) {
            throw new JolokiaCipherException(e);
        }
    }


    public String decrypt64(final String encryptedText, final String password)
            throws JolokiaCipherException {
        try {
            byte[] allEncryptedBytes = Base64Util.decode(encryptedText);

            int totalLen = allEncryptedBytes.length;

            byte[] salt = new byte[SALT_SIZE];

            System.arraycopy(allEncryptedBytes, 0, salt, 0, SALT_SIZE);

            byte padLen = allEncryptedBytes[SALT_SIZE];

            byte[] encryptedBytes = new byte[totalLen - SALT_SIZE - 1 - padLen];

            System.arraycopy(allEncryptedBytes, SALT_SIZE + 1, encryptedBytes, 0, encryptedBytes.length);

            Cipher cipher = createCipher(password.getBytes(STRING_ENCODING), salt, Cipher.DECRYPT_MODE);

            byte[] clearBytes = cipher.doFinal(encryptedBytes);

            return new String(clearBytes, STRING_ENCODING);
        } catch (Exception e) {
            throw new JolokiaCipherException(e);
        }
    }

    private Cipher createCipher(final byte[] pwdAsBytes, byte[] salt, final int mode)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
        _digester.reset();

        byte[] keyAndIv = new byte[SPICE_SIZE * 2];

        if (salt == null || salt.length == 0) {
            // Unsalted!  Bad idea!
            salt = null;
        }

        byte[] result;

        int currentPos = 0;

        while (currentPos < keyAndIv.length) {
            _digester.update(pwdAsBytes);

            if (salt != null) {
                // First 8 bytes of salt ONLY!  That wasn't obvious to me
                // when using AES encrypted private keys in "Traditional
                // SSLeay Format".
                //
                // Example:
                // DEK-Info: AES-128-CBC,8DA91D5A71988E3D4431D9C2C009F249
                //
                // Only the first 8 bytes are salt, but the whole thing is
                // re-used again later as the IV.  MUCH gnashing of teeth!
                _digester.update(salt, 0, 8);
            }
            result = _digester.digest();

            int stillNeed = keyAndIv.length - currentPos;

            // Digest gave us more than we need.  Let's truncate it.
            if (result.length > stillNeed) {
                byte[] b = new byte[stillNeed];

                System.arraycopy(result, 0, b, 0, b.length);

                result = b;
            }

            System.arraycopy(result, 0, keyAndIv, currentPos, result.length);

            currentPos += result.length;

            if (currentPos < keyAndIv.length) {
                // Next round starts with a hash of the hash.
                _digester.reset();
                _digester.update(result);
            }
        }

        byte[] key = new byte[SPICE_SIZE];

        byte[] iv = new byte[SPICE_SIZE];

        System.arraycopy(keyAndIv, 0, key, 0, key.length);

        System.arraycopy(keyAndIv, key.length, iv, 0, iv.length);

        Cipher cipher = Cipher.getInstance(CIPHER_ALG);

        cipher.init(mode, new SecretKeySpec(key, KEY_ALG), new IvParameterSpec(iv));

        return cipher;
    }




}
