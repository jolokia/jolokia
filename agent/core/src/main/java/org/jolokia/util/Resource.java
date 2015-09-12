package org.jolokia.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by nevenr on 12/09/2015.
 */
public class Resource {

    public static String getResourceAsString(String pPath) throws IOException {

        InputStream in = ClassUtil.getResourceAsStream(pPath);
        InputStreamReader is = new InputStreamReader(in);
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(is);

        String read = br.readLine();

        while (read != null) {
            sb.append(read);
            read = br.readLine();
        }

        return sb.toString();
    }

}
