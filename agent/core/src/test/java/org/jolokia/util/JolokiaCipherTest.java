package org.jolokia.util;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author nevenr
 * @since  12/09/2015
 */
public class JolokiaCipherTest {

    JolokiaCipher _cipher;

    String _cleatText = "veryOpenText";

    String _encryptedText = "ibeHrdCOonkH7d7YnH7sarQLbwOk1ljkkM/z8hUhl4c=";

    String _password = "testtest";


    @BeforeMethod
    public void setUp() throws JolokiaCipherException {
        _cipher = new JolokiaCipher();
    }


    @Test
    public void testRoundTrip() throws Exception {
        String encrypted = _cipher.encrypt64(_cleatText, _password);
        String decrypted = _cipher.decrypt64(encrypted, _password);
        assertEquals(decrypted, _cleatText);
    }

    @Test
    public void testEncrypt() throws Exception {
        String enc = _cipher.encrypt64(_cleatText, _password);

        assertNotNull(enc);

        System.out.println(enc);

        Thread.sleep(1000);

        String enc2 = _cipher.encrypt64(_cleatText, _password);

        assertNotNull(enc2);

        System.out.println(enc2);

        assertFalse(enc.equals(enc2));
    }

    @Test
    public void testDecrypt() throws Exception {
        String clear = _cipher.decrypt64(_encryptedText, _password);

        assertEquals(_cleatText, clear);
    }

    @Test
    public void testEncoding() throws Exception {
        System.out.println("file.encoding=" + System.getProperty("file.encoding"));

        String pwd = "äüöÜÖÄß\"§$%&/()=?é";
        String encPwd = _cipher.encrypt64(pwd, pwd);
        String decPwd = _cipher.decrypt64(encPwd, pwd);
        assertEquals(pwd, decPwd);
    }
}