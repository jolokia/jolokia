package org.jolokia.jvmagent.client.util;

/*
 * Copyright 2009-2013 Roland Huss
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
 * Exception indicating an error during operation of the agent launcher.
 *
 * @author roland
 * @since 12.08.11
 */
public class ProcessingException extends RuntimeException {
    private boolean quiet;
    private boolean verbose;
    private String command;

    /**
     * Constructor
     *
     * @param pErrMsg error message
     * @param pStoredExp the original exception
     * @param pOptions options from where to take the information how the error should be logged
     */
    public ProcessingException(String pErrMsg, Throwable pStoredExp, OptionsAndArgs pOptions) {
        super(pErrMsg,pStoredExp);
        quiet = pOptions.isQuiet();
        verbose = pOptions.isVerbose();
        command = pOptions.getCommand();
    }

    /**
     * Print this exception to standard error, but only if no <code>--quiet</code> is given. If
     * <code>--verbose</code> is given, then an stacktrace is printed as well.
     */
    @SuppressWarnings({"PMD.SystemPrintln"})
    public void printErrorMessage() {
        if (!quiet) {
            String msg = getCause().getMessage();
            System.err.println(getMessage() + " (command: " + command + ")" + (msg != null ? " : " + msg : ""));
        }
        if (verbose) {
            getCause().printStackTrace(System.err);
        }
    }
}
