package org.jolokia.jvmagent.client.command;

import org.jolokia.jvmagent.client.util.OptionsAndArgs;
import org.jolokia.jvmagent.client.util.VirtualMachineHandler;
import org.jolokia.util.ClassUtil;
import org.jolokia.util.JolokiaCipher;
import org.jolokia.util.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;

/**
 * @author nevenr
 * @since  12/09/2015.
 */
public class EncryptCommand extends AbstractBaseCommand {

    @Override
    String getName() {
        return "encrypt";
    }

    @Override
    int execute(OptionsAndArgs pOpts, Object pVm, VirtualMachineHandler pHandler) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        try {
            String keystorePassword = parseKeystorePassword(pOpts.toAgentArg());
            String key = Resource.getResourceAsString("META-INF/encrypt-command-password-default");
            JolokiaCipher jolokiaCipher = new JolokiaCipher();
            String encrypted = jolokiaCipher.encrypt64(keystorePassword, key);
            System.out.printf("{%s}%n", encrypted);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return 0;
    }

    private String parseKeystorePassword(String params) {
        String key = "encrypt=";
        int i = params.indexOf(key);
        return params.substring(i + key.length());
    }

}
