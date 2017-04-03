package org.jolokia.test.util;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.*;
import java.net.*;

/**
 * Utility methods useful for unit tests when interacting with the
 * runtime environment where the tests are executed.
 *
 * @author roland
 * @since 01.09.11
 */
public class EnvTestUtil {

    private EnvTestUtil() {}

    /**
     * Get an arbitrary free port on the localhost
     *
     * @return free port
     * @throws IllegalArgumentException if no free port could be found
     */
    public static int getFreePort() throws IOException {
        for (int port = 22332; port < 22500;port++) {
            if (trySocket(port)) {
                return port;
            }
        }
        throw new IllegalStateException("Cannot find a single free port");
    }

    /**
     * Try a given port on the localhost and check whether it is free
     *
     * @param port port to check
     * @return true if the port is still free
     */
    @SuppressWarnings({"PMD.SystemPrintln"})
    public static boolean trySocket(int port) throws IOException {
        InetAddress address = Inet4Address.getByName("localhost");
        ServerSocket s = null;
        try {
            s = new ServerSocket();
            s.bind(new InetSocketAddress(address,port));
            return true;
        } catch (IOException exp) {
            System.err.println("Port " + port + " already in use, trying next ...");
            // exp.printStackTrace();
            // next try ....
        } finally {
            if (s != null) {
                s.close();
            }
        }
        return false;
    }

    /**
     * Read an input stream into a plain string
     *
     * @param is input stream to read from
     * @return string containing the content
     * @throws IOException if something fails
     */
    public static String readToString(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte buffer[] = new byte[8192];
        int read = -1;
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
            os.flush();
        }
        return new String(os.toByteArray());
    }
}
