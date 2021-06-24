package org.jolokia.jvmagent.client.command;

import org.jolokia.jvmagent.client.util.OptionsAndArgs;
import org.jolokia.jvmagent.client.util.VirtualMachineHandlerOperations;
import org.jolokia.util.*;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Command for encrypting a password which can be used in the configuration
 * to e.g. access a keystore.
 *
 * @author nevenr
 * @since  12/09/2015.
 */
public class EncryptCommand extends AbstractBaseCommand {

    private final JolokiaCipher.KeyHolder keyHolder;

    public EncryptCommand() {
        this(null);
    }

    public EncryptCommand(JolokiaCipher.KeyHolder pKeyHolder) {
        this.keyHolder = pKeyHolder;
    }

    @Override
    String getName() {
        return "encrypt";
    }

    @Override
    int execute(OptionsAndArgs pOpts, Object pVm, VirtualMachineHandlerOperations pHandler)
            throws InvocationTargetException {
        try {
            List<String> args = pOpts.getExtraArgs();
            String password = args.size() == 0 ?
                    getPasswordFromConsoleOrStdin(pOpts) :
                    args.get(0);
            JolokiaCipher jolokiaCipher = keyHolder != null ?
                    new JolokiaCipher(keyHolder) :
                    new JolokiaCipher();
            String encrypted = jolokiaCipher.encrypt(password);
            System.out.printf("[[%s]]%n", encrypted);
            return 0;
        } catch (GeneralSecurityException exp) {
            throw new InvocationTargetException(exp,"Can not encrypt password");
        }
    }

    private String getPasswordFromConsoleOrStdin(OptionsAndArgs pOpts) {
        Console console = System.console();
        if (console != null) {
            char[] password = console.readPassword(pOpts.isQuiet() ? "" : "Enter password : ");
            return new String(password);
        } else {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            try {
                return reader.readLine();
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot read password from standard input: " + e);
            }
        }
    }


}
