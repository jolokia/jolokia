package org.jolokia.jvmagent.client.command;

import org.jolokia.jvmagent.client.util.OptionsAndArgs;
import org.jolokia.jvmagent.client.util.VirtualMachineHandler;
import org.jolokia.util.JolokiaCipher;
import org.jolokia.util.JolokiaCipherPasswordProvider;
import org.jolokia.util.Resource;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * @author nevenr
 * @since  12/09/2015.
 */
public class EncryptCommand extends AbstractBaseCommand {

    private JolokiaCipherPasswordProvider passwordProvider;

    public EncryptCommand() {
        this.passwordProvider = new JolokiaCipherPasswordProvider();
    }

    public EncryptCommand(JolokiaCipherPasswordProvider passwordProvider) {
        this.passwordProvider = passwordProvider;
    }

    @Override
    String getName() {
        return "encrypt";
    }

    @Override
    int execute(OptionsAndArgs pOpts, Object pVm, VirtualMachineHandler pHandler) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        try {
            String keystorePassword = parseKeystorePassword(pOpts.toAgentArg());
            JolokiaCipher jolokiaCipher = new JolokiaCipher();
            String key = passwordProvider.getDefaultKey();
            String encrypted = jolokiaCipher.encrypt64(keystorePassword, key);
            System.out.printf("{%s}%n", encrypted);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return 0;
    }


    private String parseKeystorePassword(String params) throws IOException {
        String key = "encrypt=";
        int i = params.indexOf(key);
        String passwd = params.substring(i + key.length());

        // It is not possible to have option that works with and without argument so
        // value ! represent special case when user want to type in password from within application
        // and not on command line during start
        if (!"!".equals(passwd)){
            return passwd;
        }

        return Resource.readStdinAsString();

    }

}
