package org.jolokia.util;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.json.simple.JSONStreamAware;

/**
 * @author roland
 * @since 23.01.18
 */
public class IoUtil {

    // Marker for the end of the stream
    private final static char[] STREAM_END_MARKER = {};

    private IoUtil() { }

    /**
     * Stream a JSON stream to a given writer, potentiall wrap it in a callback for a
     * JSONP response and then flush & close the writer. The writer is closed in any case,
     * also when an exception occurs
     *
     * @param pWriter writer to write to. Must be not null.
     * @param pJson JSON response to stream
     * @param callback the name of the callback function if JSONP should be used or <code>null</code> if the answer should be streamed directly
     * @throws IOException if the streaming fails
     */
    public static void streamResponseAndClose(Writer pWriter, JSONStreamAware pJson, String callback)
        throws IOException {
        try {
            if (callback == null) {
                pJson.writeJSONString(pWriter);
            } else {
                pWriter.write(callback);
                pWriter.write("(");
                pJson.writeJSONString(pWriter);
                pWriter.write(");");
            }
            // Writer end marker for chunked responses
            pWriter.write(STREAM_END_MARKER);
        } finally {
            // Flush and close, even on an exception to avoid locks in the thread
            pWriter.flush();
            pWriter.close();
        }
    }
}
