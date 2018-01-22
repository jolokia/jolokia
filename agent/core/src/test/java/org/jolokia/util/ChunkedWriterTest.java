package org.jolokia.util;

import org.testng.annotations.Test;
import sun.misc.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.testng.Assert.*;

/**
 * Created by gnufied on 2/8/16.
 */
public class ChunkedWriterTest {

    private static int CHUNK_SIZE = 8192;

    @Test
    public void checkFlush() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ChunkedWriter writer = new ChunkedWriter(out, "UTF-8");
        writer.write("hello");
        writer.flush();
        assertEquals(out.size(), 5);
    }

    @Test
    public void checkSmallWrite() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ChunkedWriter writer = new ChunkedWriter(out, "UTF-8");
        writer.write("hello");
        assertEquals(out.size(), 0);
    }

    @Test
    public void checkBigWrite() throws IOException {
        int overflow = 100;

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        String space10 = new String(new char[CHUNK_SIZE + overflow]).replace('\0', ' ');

        ChunkedWriter writer = new ChunkedWriter(out, "UTF-8");
        writer.write(space10);
        assertEquals(out.size(), CHUNK_SIZE);
        writer.flush();
        assertEquals(out.size(), CHUNK_SIZE + overflow);
    }

    @Test
    public void checkClose() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ChunkedWriter writer = new ChunkedWriter(out, "UTF-8");
        assertTrue(writer.isOpen());
        writer.close();

        assertFalse(writer.isOpen());
    }
}
