package org.jolokia.jvmagent.client.command;

import org.jolokia.jvmagent.client.util.OptionsAndArgs;
import org.jolokia.util.JolokiaCipher;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;

/**
 * @author nevenr
 * @since  18/09/2015
 */
public class EncryptCommandTest {

    class JCPPTest implements JolokiaCipher.KeyHolder {
        @Override
        public String getKey() {
            return "changeit";
        }
    }

    @Test
    public void testEncryptDecrypt() throws Exception {
        String passwd = "test 123";

        JCPPTest keyHolder = new JCPPTest();

        EncryptCommand ec = new EncryptCommand(keyHolder);
        OptionsAndArgs oaa = new OptionsAndArgs(CommandDispatcher.getAvailableCommands(),"encrypt", passwd);
        PrintStream original = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        ec.execute(oaa, null, null);
        System.setOut(original);

        Matcher matcher = Pattern.compile("^\\[\\[(.*)]]").matcher(baos.toString());
        matcher.find();
        String encrypted = matcher.group(1);

        JolokiaCipher jolokiaCipher = new JolokiaCipher(keyHolder);
        String decrypted = jolokiaCipher.decrypt(encrypted);
        assertEquals(decrypted,passwd);
    }

}
