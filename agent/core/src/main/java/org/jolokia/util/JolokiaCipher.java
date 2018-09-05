package org.jolokia.util;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.io.*;
import java.nio.charset.Charset;
import java.security.*;
import java.util.Random;

/**
 * Simple symmetric, salted encryption.
 *
 * Most of the code has been borrowed from
 * <a href="https://github.com/sonatype/plexus-cipher/blob/plexus-cipher-1.7/src/main/java/org/sonatype/plexus/components/cipher/PBECipher.java">
 * Plexus</a> code
 *
 * @author nevenr
 * @since  12/09/2015
 */
public class JolokiaCipher {

    private static final int SALT_SIZE = 8;
    private static final int CHUNK_SIZE = 16;
    public static final String JOLOKIA_CYPHER_PASSWORD = "META-INF/jolokia-password";

    private MessageDigest digest;
    private Random random;

    private KeyHolder keyHolder;

    public JolokiaCipher() throws GeneralSecurityException {
        this(new KeyHolderImpl());
    }

    public JolokiaCipher(KeyHolder pKeyHolder) throws NoSuchAlgorithmException {
        digest = MessageDigest.getInstance("SHA-256");
        random = new SecureRandom();
        keyHolder = pKeyHolder;

        // Automatic decryption of passwords with Jolokia isnt secure
        // anyway (but only stops getting the password by accident),
        // so we are going to use a simple seed for the salt
        random.setSeed(System.currentTimeMillis());
    }

    /**
     * Encrypt a string with a password.
     *
     * @param pText text to encode
     * @return the encoded password
     */
    public String encrypt(final String pText) throws GeneralSecurityException {
        byte[] clearBytes = pText.getBytes(Charset.forName("UTF-8"));
        byte[] salt = getSalt(SALT_SIZE);

        Cipher cipher = createCipher(salt, Cipher.ENCRYPT_MODE);
        byte[] encryptedBytes = cipher.doFinal(clearBytes);
        int len = encryptedBytes.length;

        byte padLen = (byte) (CHUNK_SIZE - (SALT_SIZE + len + 1) % CHUNK_SIZE);
        int totalLen = SALT_SIZE + len + padLen + 1;

        byte[] allEncryptedBytes = getSalt(totalLen);
        System.arraycopy(salt, 0, allEncryptedBytes, 0, SALT_SIZE);
        allEncryptedBytes[SALT_SIZE] = padLen;
        System.arraycopy(encryptedBytes, 0, allEncryptedBytes, SALT_SIZE + 1, len);

        return Base64Util.encode(allEncryptedBytes);
    }

    /**
     * Decrypt a password encrypted with {@link #encrypt(String)}
     *
     * @param pEncryptedText encrypted text
     * @return the decrypted text
     * @throws GeneralSecurityException when decryption fails
     */
    public String decrypt(final String pEncryptedText) throws GeneralSecurityException {
        byte[] allEncryptedBytes = Base64Util.decode(pEncryptedText);
        int totalLen = allEncryptedBytes.length;
        byte[] salt = new byte[SALT_SIZE];

        System.arraycopy(allEncryptedBytes, 0, salt, 0, SALT_SIZE);
        byte padLen = allEncryptedBytes[SALT_SIZE];

        byte[] encryptedBytes = new byte[totalLen - SALT_SIZE - 1 - padLen];
        System.arraycopy(allEncryptedBytes, SALT_SIZE + 1, encryptedBytes, 0, encryptedBytes.length);

        Cipher cipher = createCipher(salt, Cipher.DECRYPT_MODE);
        byte[] clearBytes = cipher.doFinal(encryptedBytes);

        return new String(clearBytes, Charset.forName("UTF-8"));
    }

    // =================================================================

    private byte[] getSalt(final int sz) {
        byte[] res = new byte[sz];
        random.nextBytes(res);
        return res;
    }

    private Cipher createCipher(byte[] salt, final int mode)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
        digest.reset();

        byte[] pwdAsBytes = getKeyAsBytes();
        byte[] keyAndIv = new byte[16 * 2];
        byte[] result;

        int currentPos = 0;

        while (currentPos < keyAndIv.length) {
            digest.update(pwdAsBytes);
            digest.update(salt, 0, 8);
            result = digest.digest();

            int stillNeed = keyAndIv.length - currentPos;

            if (result.length > stillNeed) {
                byte[] b = new byte[stillNeed];
                System.arraycopy(result, 0, b, 0, b.length);
                result = b;
            }

            System.arraycopy(result, 0, keyAndIv, currentPos, result.length);
            currentPos += result.length;
            if (currentPos < keyAndIv.length) {
                digest.reset();
                digest.update(result);
            }
        }

        byte[] key = new byte[16];
        byte[] iv = new byte[16];

        System.arraycopy(keyAndIv, 0, key, 0, key.length);
        System.arraycopy(keyAndIv, key.length, iv, 0, iv.length);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));

        return cipher;
    }

    // =====================================================================

    public interface KeyHolder { String getKey(); };

    private byte[] getKeyAsBytes() {
        return keyHolder.getKey().getBytes(Charset.forName("UTF-8"));
    }

    private static class KeyHolderImpl implements KeyHolder {

        public String getKey() {
            InputStream in = ClassUtil.getResourceAsStream(JOLOKIA_CYPHER_PASSWORD);
            if (in != null) {
                try {
                    return new BufferedReader(new InputStreamReader(in)).readLine();
                } catch (IOException e) {
                    throw new IllegalStateException("Can not read password from " + JOLOKIA_CYPHER_PASSWORD + ": " + e,e);
                }
            } else {
                return "`x%_rDL9T'&ENuyA{LPcc(UDv`NzzY6NZF\"F=rba-9Ftg,HJr.y@E;amfr>B4z<UqQg}2_4kq\\Y@6mNJEpwGx#CT;&?%%.$T_br`(&%3)2vC:5?3f9ptX?KR9kYQu2;#".substring(40, 72);
            }
        }
    }
}
