package org.jolokia.util;

import java.security.GeneralSecurityException;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author nevenr
 * @since  12/09/2015
 */
public class JolokiaCipherTest {

    JolokiaCipher cipher;
    String clearText = "veryOpenText";
    String encryptedText = "ibeHrdCOonkH7d7YnH7sarQLbwOk1ljkkM/z8hUhl4c=";

    @BeforeMethod
    public void setUp() throws GeneralSecurityException {
        cipher = new JolokiaCipher(new JolokiaCipher.KeyHolder() {
            public String getKey() {
                return "testtest";
            }
        });
    }

    @Test
    public void testRoundTrip() throws Exception {
        String encrypted = cipher.encrypt(clearText);
        String decrypted = cipher.decrypt(encrypted);
        assertEquals(decrypted, clearText);
    }

    @Test
    public void testSalt() throws Exception {
        String enc = cipher.encrypt(clearText);
        assertNotNull(enc);
        String enc2 = cipher.encrypt(clearText);
        assertNotNull(enc2);
        assertNotEquals(enc,enc2);
    }

    @Test
    public void testDecrypt() throws Exception {
        String clear = cipher.decrypt(encryptedText);
        assertEquals(clearText, clear);
    }

    @Test
    public void testEncoding() throws Exception {
        String pwd = "äüöÜÖÄß\"§$%&/()=?é";
        String encPwd = cipher.encrypt(pwd);
        String decPwd = cipher.decrypt(encPwd);
        assertEquals(pwd, decPwd);
    }
}