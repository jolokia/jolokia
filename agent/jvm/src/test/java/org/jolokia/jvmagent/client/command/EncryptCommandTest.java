package org.jolokia.jvmagent.client.command;

import org.jolokia.jvmagent.client.util.OptionsAndArgs;
import org.jolokia.util.JolokiaCipher;
import org.jolokia.util.JolokiaCipherPasswordProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import static org.testng.Assert.assertEquals;

/**
 * @author nevenr
 * @since  18/09/2015
 */
public class EncryptCommandTest {

    class JCPPTest extends JolokiaCipherPasswordProvider {
        @Override
        public String getDefaultKey() throws IOException {
            return "changeit";
        }
    }

    @Test
    public void testEncryptDecrypt() throws Exception {
        String passwd = "test 123";

        JCPPTest passwordProvider = new JCPPTest();

        EncryptCommand ec = new EncryptCommand(passwordProvider);
        OptionsAndArgs oaa = new OptionsAndArgs(CommandDispatcher.getAvailableCommands(),"--encrypt", passwd);
        PrintStream original = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        ec.execute(oaa, null, null);
        System.setOut(original);
        String encripted = baos.toString();
        System.out.println("encripted:" + encripted);


        JolokiaCipher jolokiaCipher = new JolokiaCipher();
        String decripted = jolokiaCipher.decrypt64(encripted.substring(1, encripted.length() - 1), passwordProvider.getDefaultKey());
        System.out.println("decripted:" + decripted);

        assertEquals(decripted,passwd);

    }

}
