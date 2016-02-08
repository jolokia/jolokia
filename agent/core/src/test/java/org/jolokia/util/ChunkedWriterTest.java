package org.jolokia.util;

import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.testng.Assert.*;

/**
 * Created by gnufied on 2/8/16.
 */
public class ChunkedWriterTest {
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
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        String space10 = new String(new char[4100]).replace('\0', ' ');

        ChunkedWriter writer = new ChunkedWriter(out, "UTF-8");
        writer.write(space10);
        assertEquals(out.size(), 4096);
        writer.flush();
        assertEquals(out.size(), 4100);
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
