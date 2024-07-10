package org.jolokia.server.core.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;


public class IoUtilTest {
    @SuppressWarnings("unchecked")
    @Test
    public void checkSmallWrite() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

        JSONObject resp = new JSONObject();
        resp.put("value", "hello");
        String respString = resp.toJSONString();

        IoUtil.streamResponseAndClose(writer,resp,null);
        assertEquals(out.size(), respString.length());
        assertEquals(out.toString(StandardCharsets.UTF_8), respString);
        assertWriterClosed(writer);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void checkBigWrite() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

        // Resp which should be bigger than 8192, the buffer size of OutputStreamWriter
        JSONArray resp = new JSONArray();
        for (int i = 0; i < 10000; i++) {
            JSONObject data = new JSONObject();
            data.put("value", "hello");
            resp.add(data);
        }
        String respString = resp.toJSONString();

        IoUtil.streamResponseAndClose(writer,resp,null);
        assertEquals(out.size(), respString.length());
        assertEquals(out.toString(StandardCharsets.UTF_8), respString);
        assertWriterClosed(writer);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void checkWriteWithCallback() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

        JSONObject resp = new JSONObject();
        resp.put("value", "hello");
        String respString = "callbackFunc(" + resp.toJSONString() + ");";

        IoUtil.streamResponseAndClose(writer,resp,"callbackFunc");
        assertEquals(out.size(), respString.length());
        assertEquals(out.toString(StandardCharsets.UTF_8), respString);
        assertWriterClosed(writer);
    }

    private void assertWriterClosed(OutputStreamWriter writer) {
        try {
            writer.write(1);
            fail("Writer is not closed");
        } catch (IOException exp) {
            assertTrue(exp.getMessage().contains("closed"));
        }
    }
}
